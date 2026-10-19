/*
AI Assistance Disclosure:
Tool: Claude Code (Fable 5), date: 2026-10-11
Scope: MockMvc tests for issue #94's password-reset routes, in
       AuthControllerTest's shape (Testcontainers Postgres, the
       OtpEmailSender mocked to capture the code). The request cases
       pin the one observable answer — an empty 202 — for known and
       unknown emails alike; the round-trip cases run the real
       service against the real schema (V7 included) and prove the
       new password logs in, the code is single-use, exhausted
       attempts answer the same 400 as a wrong code (no revealing
       429), and a deleted account resets without being recovered.
       Rewritten from the link-token version the same day, when the
       owner revisited the credential to an OTP code.
Author review: Leong Wei Zhi to review via the PR.
2026-10-12 (Claude Code, Fable 5), PR #179 review (@Sinnez1): the
exhaustion case now also proves a repeat request buys no fresh budget
while the spent row lives, and the round trip pins the session
eviction (TokenDenylist mocked like LogoutTest's).
2026-10-17 (Claude Code, Opus 5), PR #183 review (@Sinnez1): the round
trip also pins that eviction as landing after the commit, read over a
second connection — the reviewer found the pre-commit stamp in
AdminService and the owner chose to fix this call site with it.
*/

package foc.user.controller;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import foc.user.PostgresTestContainer;
import foc.user.entity.PasswordResetOtp;
import foc.user.entity.Role;
import foc.user.entity.User;
import foc.user.repository.PasswordResetOtpRepository;
import foc.user.repository.UserRepository;
import foc.user.security.TokenDenylist;
import foc.user.service.OtpEmailSender;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;

@SpringBootTest
@AutoConfigureMockMvc
class PasswordResetControllerTest extends PostgresTestContainer {

    private static final String EMAIL = "e1234567@u.nus.edu";
    private static final String OLD_PASSWORD = "OldPassword123";
    private static final String NEW_PASSWORD = "NewPassword456";
    private static final String INVALID_CODE =
        "Reset code is invalid or has expired; request a new one";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetOtpRepository resetOtpRepository;

    // reads the row over a connection of its own, so it answers with
    // committed state only (PR #183 review)
    @Autowired
    private DataSource dataSource;

    // captures the plain code instead of speaking SMTP; reset per test
    @MockitoBean
    private OtpEmailSender otpEmailSender;

    // the Redis writer, mocked like LogoutTest's: no Redis runs in the
    // test stack, and the write itself is unit-tested
    @MockitoBean
    private TokenDenylist tokenDenylist;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    private User alex;

    @BeforeEach
    void seedUser() {
        resetOtpRepository.deleteAll();
        userRepository.deleteAll();
        alex = userRepository.save(
            new User(EMAIL, "student_alex", encoder.encode(OLD_PASSWORD), Role.USER));
    }

    // a reset row outliving its test breaks the other classes' setup:
    // they call userRepository.deleteAll() without knowing this FK'd
    // table exists (AccountUpdateControllerTest's rule, PR #179
    // approval follow-up — @Sinnez1 hit 48 failures from one left row)
    @AfterEach
    void clearResetRows() {
        resetOtpRepository.deleteAll();
    }

    private ResultActions postJson(String path, Map<String, String> body) throws Exception {
        return mockMvc.perform(post(path)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)));
    }

    private ResultActions requestReset(String email) throws Exception {
        return postJson("/auth/password-reset", Map.of("email", email));
    }

    private ResultActions confirmReset(String email, String code, String newPassword,
            String confirmPassword) throws Exception {
        return postJson("/auth/password-reset/confirm", Map.of(
            "email", email, "code", code,
            "newPassword", newPassword, "confirmPassword", confirmPassword));
    }

    // the last code the mocked sender was handed
    private String emailedCode() {
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(otpEmailSender).sendPasswordResetCode(eq(EMAIL), code.capture(), any());
        return code.getValue();
    }

    // the password hash the database holds for alex, as a connection
    // outside the request's transaction sees it
    private String committedHash() {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                    "SELECT password_hash FROM users WHERE id = ?")) {
            statement.setLong(1, alex.getId());
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? row.getString(1) : null;
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("Request answers an empty 202 and mails a code that confirms")
    void resetRoundTrip() throws Exception {
        requestReset(EMAIL)
            .andExpect(status().isAccepted())
            .andExpect(content().string(""));

        String[] hashWhenStamped = new String[1];
        doAnswer(invocation -> {
            hashWhenStamped[0] = committedHash();
            return null;
        }).when(tokenDenylist).revokeUserSessions(alex.getId());

        confirmReset(EMAIL, emailedCode(), NEW_PASSWORD, NEW_PASSWORD)
            .andExpect(status().isNoContent());

        User reloaded = userRepository.findById(alex.getId()).orElseThrow();
        assertThat(encoder.matches(NEW_PASSWORD, reloaded.getPasswordHash())).isTrue();
        // single use: the consumed row is gone
        assertThat(resetOtpRepository.findAll()).isEmpty();

        // the new password is a real credential: login takes it
        postJson("/auth/login", Map.of("usernameOrEmail", EMAIL, "password", NEW_PASSWORD))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty());

        // and the old password's sessions were evicted (PR #179 review):
        // the gateway refuses this account's pre-reset tokens
        verify(tokenDenylist).revokeUserSessions(alex.getId());
        // after the commit (PR #183 review, @Sinnez1): stamped from inside
        // the transaction, the cutoff preceded the new hash becoming
        // visible, so an old-password login in that gap minted a token the
        // cutoff accepts
        assertThat(encoder.matches(NEW_PASSWORD, hashWhenStamped[0]))
            .as("the new password was committed before the stamp").isTrue();
    }

    @Test
    @DisplayName("An unknown email gets the identical empty 202, and no mail goes out")
    void requestReset_unknownEmailSameAnswer() throws Exception {
        requestReset("e9999999@u.nus.edu")
            .andExpect(status().isAccepted())
            .andExpect(content().string(""));

        verify(otpEmailSender, never()).sendPasswordResetCode(anyString(), anyString(), any());
        assertThat(resetOtpRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("A malformed email is refused on format alone — that reveals nothing")
    void requestReset_malformedEmail() throws Exception {
        requestReset("not-an-nus-email@gmail.com")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value(
                "Email must be a valid @u.nus.edu address. Email used should not be the friendly email."));
    }

    @Test
    @DisplayName("A used code answers the invalid-code 400 the second time")
    void confirmReset_singleUse() throws Exception {
        requestReset(EMAIL).andExpect(status().isAccepted());
        String code = emailedCode();

        confirmReset(EMAIL, code, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());
        confirmReset(EMAIL, code, "AnotherPass789", "AnotherPass789")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.type").value("urn:foc:user:reset-code-invalid"))
            .andExpect(jsonPath("$.detail").value(INVALID_CODE));

        // the second attempt changed nothing
        User reloaded = userRepository.findById(alex.getId()).orElseThrow();
        assertThat(encoder.matches(NEW_PASSWORD, reloaded.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("A wrong code and an unknown email answer identically")
    void confirmReset_wrongCodeAndUnknownEmailIndistinguishable() throws Exception {
        requestReset(EMAIL).andExpect(status().isAccepted());

        for (String email : new String[] {EMAIL, "e9999999@u.nus.edu"}) {
            confirmReset(email, "000000", NEW_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:foc:user:reset-code-invalid"))
                .andExpect(jsonPath("$.detail").value(INVALID_CODE));
        }
    }

    @Test
    @DisplayName("Exhausted attempts answer the same 400 — and the right code stops working")
    void confirmReset_attemptsExhaustSilently() throws Exception {
        requestReset(EMAIL).andExpect(status().isAccepted());
        String code = emailedCode();

        // five wrong guesses: every answer identical, no 429, no reveal
        for (int i = 0; i < 5; i++) {
            confirmReset(EMAIL, "000000", NEW_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:foc:user:reset-code-invalid"))
                .andExpect(jsonPath("$.detail").value(INVALID_CODE));
        }
        // the row is spent: even the real code now answers the same way
        confirmReset(EMAIL, code, NEW_PASSWORD, NEW_PASSWORD)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value(INVALID_CODE));

        User reloaded = userRepository.findById(alex.getId()).orElseThrow();
        assertThat(encoder.matches(OLD_PASSWORD, reloaded.getPasswordHash())).isTrue();

        // and a repeat request past the cooldown buys no fresh budget:
        // the spent row absorbs it (same 202, no new mail) until it
        // expires — before PR #179's review this was 5 guesses per
        // cooldown, forever
        PasswordResetOtp spent = resetOtpRepository.findAll().get(0);
        ReflectionTestUtils.setField(spent, "createdAt",
            Instant.now().minusSeconds(61));
        resetOtpRepository.save(spent);
        requestReset(EMAIL)
            .andExpect(status().isAccepted())
            .andExpect(content().string(""));
        verify(otpEmailSender, times(1))
            .sendPasswordResetCode(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Confirm re-validates the password like sign-up and checks the double entry")
    void confirmReset_validation() throws Exception {
        // allOf, not one exact sentence: two violations on one field come
        // back in no guaranteed order (AccountUpdateControllerTest's shape)
        confirmReset(EMAIL, "042042", "short", "short")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value(allOf(
                containsString("Password must be between 10 and 50 characters long."),
                containsString("Password must contain at least one uppercase letter,"
                    + " one lowercase letter, and one number."))));

        confirmReset(EMAIL, "042042", NEW_PASSWORD, "Different456")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Password entries do not match."));
    }

    @Test
    @DisplayName("A deleted account inside the window resets and stays deleted")
    void resetOnDeletedAccount_staysDeleted() throws Exception {
        alex.setDeletedAt(Instant.now().minus(10, ChronoUnit.DAYS));
        userRepository.save(alex);

        requestReset(EMAIL).andExpect(status().isAccepted());
        confirmReset(EMAIL, emailedCode(), NEW_PASSWORD, NEW_PASSWORD)
            .andExpect(status().isNoContent());

        User reloaded = userRepository.findById(alex.getId()).orElseThrow();
        assertThat(encoder.matches(NEW_PASSWORD, reloaded.getPasswordHash())).isTrue();
        // recovery is the login flow's explicit choice, not a side effect
        assertThat(reloaded.isActive()).isFalse();
    }

    @Test
    @DisplayName("An account past the window gets the same empty 202 and no mail")
    void requestReset_pastWindowSameAnswer() throws Exception {
        alex.setDeletedAt(Instant.now().minus(31, ChronoUnit.DAYS));
        userRepository.save(alex);

        requestReset(EMAIL)
            .andExpect(status().isAccepted())
            .andExpect(content().string(""));

        verify(otpEmailSender, never()).sendPasswordResetCode(anyString(), anyString(), any());
    }
}
