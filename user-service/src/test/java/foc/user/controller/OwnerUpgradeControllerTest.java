/*
AI Assistance Disclosure:
Tool: Claude Code (Opus 5.5), date: 2026-11-08
Scope: integration tests for issue #308's POST /users/me/owner against the
       shared Testcontainers Postgres, in AccountUpdateControllerTest's
       harness (mocked OtpEmailSender capturing the gate code): success
       with the session revoke, wrong and blank tokens (403, code kept),
       the unconfigured token (503), the missing gate code, an OWNER
       caller, and the unauthenticated 401. Behaviour decided by Leong Wei
       Zhi via options Q&A (2026-11-08).
Author review: Leong Wei Zhi to review via the PR.
2026-11-09 (Claude Code, Opus 5.5), PR #309 review (@Sinnez1): code-first
order (decided by Leong Wei Zhi via options Q&A, 2026-11-09): a wrong
token spends the code and the cooldown holds back the next one, guesses with a wrong code learn nothing and are
bounded by the attempt budget, and no code means no compare. No
reflection; the unconfigured-token 503 moved to OwnerSetupUnconfiguredTest.
*/

package foc.user.controller;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import foc.user.PostgresTestContainer;
import foc.user.entity.Role;
import foc.user.entity.User;
import foc.user.repository.AccountUpdateOtpRepository;
import foc.user.repository.PendingEmailChangeRepository;
import foc.user.repository.UserRepository;
import foc.user.security.TokenDenylist;
import foc.user.service.OtpEmailSender;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
class OwnerUpgradeControllerTest extends PostgresTestContainer {

    private static final String EMAIL = "e1234567@u.nus.edu";
    // PostgresTestContainer's pin
    private static final String OWNER_KEY = "test-owner-setup-token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountUpdateOtpRepository gateOtps;

    @Autowired
    private PendingEmailChangeRepository pendingEmailChanges;

    // captures the plain codes instead of speaking SMTP; reset per test
    @MockitoBean
    private OtpEmailSender otpEmailSender;

    // the test stack runs no Redis, and the mock lets the revoke be asserted
    @MockitoBean
    private TokenDenylist tokenDenylist;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private User alex;

    @BeforeEach
    void cleanDatabase() {
        gateOtps.deleteAll();
        pendingEmailChanges.deleteAll();
        userRepository.deleteAll();
        alex = userRepository.save(new User(EMAIL, "student_alex", "hashed_password", Role.USER));
    }

    @AfterEach
    void clearOtpRows() {
        gateOtps.deleteAll();
    }

    private ResultActions upgrade(Map<String, String> body) throws Exception {
        return mockMvc.perform(post("/users/me/owner").with(user(alex.getId().toString()))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)));
    }

    // request a gate code and return it
    private String freshGateCode() throws Exception {
        mockMvc.perform(post("/users/me/otp").with(user(alex.getId().toString())))
            .andExpect(status().isAccepted());
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(otpEmailSender, atLeastOnce()).sendAccountUpdateCode(eq(EMAIL), code.capture(), any());
        return code.getValue();
    }

    private Role storedRole() {
        return userRepository.findById(alex.getId()).orElseThrow().getRole();
    }

    @Test
    @DisplayName("Token and gate code upgrade the caller to OWNER and revoke their sessions")
    void upgrade_success() throws Exception {
        String code = freshGateCode();

        upgrade(Map.of("ownerKey", OWNER_KEY, "otp", code))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(alex.getId()))
            .andExpect(jsonPath("$.role").value("OWNER"));

        assertThat(storedRole()).isEqualTo(Role.OWNER);
        assertThat(gateOtps.findByUserId(alex.getId())).isEmpty();
        verify(tokenDenylist).revokeUserSessions(alex.getId());
    }

    @Test
    @DisplayName("An ADMIN caller can upgrade too")
    void upgrade_admin() throws Exception {
        alex.setRole(Role.ADMIN);
        alex = userRepository.save(alex);
        String code = freshGateCode();

        upgrade(Map.of("ownerKey", OWNER_KEY, "otp", code))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("OWNER"));
        assertThat(storedRole()).isEqualTo(Role.OWNER);
    }

    @Test
    @DisplayName("A wrong token after the right code is a typed 403 and spends the code")
    void upgrade_wrongTokenSpendsCode() throws Exception {
        String code = freshGateCode();

        upgrade(Map.of("ownerKey", "not-the-token", "otp", code))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.type").value("urn:foc:user:owner-setup-token-invalid"))
            .andExpect(jsonPath("$.detail").value(
                "Owner setup token is incorrect; request a new code to try again"))
            // application-test.yaml turns the resend cooldown off, so no
            // wait; AccountUpdateServiceTest checks a real one
            .andExpect(header().string("Retry-After", "0"));
        assertThat(storedRole()).isEqualTo(Role.USER);
        verify(tokenDenylist, never()).revokeUserSessions(any());

        // the spent code refuses even the right token
        upgrade(Map.of("ownerKey", OWNER_KEY, "otp", code))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.type").value("urn:foc:user:otp-attempts-exceeded"));
        // the row is kept, so its lastSentAt still gates the next code
        assertThat(gateOtps.findByUserId(alex.getId())).isPresent();
        assertThat(storedRole()).isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("Repeated token guesses with a wrong code answer as wrong codes until the code is spent")
    void upgrade_guessesWithoutCodeLearnNothing() throws Exception {
        String code = freshGateCode();
        String wrongCode = code.equals("000000") ? "000001" : "000000";

        for (int i = 0; i < 4; i++) {
            upgrade(Map.of("ownerKey", "guess-" + i, "otp", wrongCode))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:foc:user:otp-invalid"));
        }
        // the fifth wrong code exhausts the budget (user.otp.max-attempts=5)
        upgrade(Map.of("ownerKey", OWNER_KEY, "otp", wrongCode))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.type").value("urn:foc:user:otp-attempts-exceeded"));
        // the spent code refuses even the right pair
        upgrade(Map.of("ownerKey", OWNER_KEY, "otp", code))
            .andExpect(status().isTooManyRequests());
        assertThat(storedRole()).isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("A blank token is refused like a wrong one")
    void upgrade_blankToken() throws Exception {
        String code = freshGateCode();

        upgrade(Map.of("ownerKey", "", "otp", code))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.type").value("urn:foc:user:owner-setup-token-invalid"));
        assertThat(storedRole()).isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("A missing token is refused like a wrong one")
    void upgrade_missingToken() throws Exception {
        String code = freshGateCode();

        upgrade(Map.of("otp", code))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.type").value("urn:foc:user:owner-setup-token-invalid"));
        assertThat(storedRole()).isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("A token guess without a requested gate code is refused before the token is compared")
    void upgrade_noGateCode() throws Exception {
        upgrade(Map.of("ownerKey", "not-the-token", "otp", "123456"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.type").value("urn:foc:user:otp-required"));
        upgrade(Map.of("ownerKey", OWNER_KEY, "otp", "123456"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.type").value("urn:foc:user:otp-required"));
        assertThat(storedRole()).isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("A missing code is a validation error")
    void upgrade_missingCode() throws Exception {
        upgrade(Map.of("ownerKey", OWNER_KEY))
            .andExpect(status().isBadRequest());
        assertThat(storedRole()).isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("An OWNER caller gets the account back unchanged, code kept, nothing revoked")
    void upgrade_alreadyOwner() throws Exception {
        alex.setRole(Role.OWNER);
        alex = userRepository.save(alex);
        String code = freshGateCode();

        upgrade(Map.of("ownerKey", OWNER_KEY, "otp", code))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("OWNER"));
        assertThat(gateOtps.findByUserId(alex.getId())).isPresent();
        verify(tokenDenylist, never()).revokeUserSessions(any());
    }

    @Test
    @DisplayName("POST /users/me/owner needs a signed-in caller")
    void upgrade_unauthenticated() throws Exception {
        mockMvc.perform(post("/users/me/owner").contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnauthorized());
    }
}
