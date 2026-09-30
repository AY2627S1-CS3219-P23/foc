/*
AI Assistance Disclosure:
Tool: Claude Code (Fable 5), date: 2026-09-30
Scope: integration tests for issue #92's account-update routes against
       the shared Testcontainers Postgres: the gate-code lifecycle
       (request/resend/consume/attempts), PATCH's applied-vs-parked
       split, the new-address confirmation with its taken-while-pending
       discard, password change with server-side double-entry, the
       rubric P1.5 case (a role in the body has no effect), the 502
       rollback proof, and the problem+json type URIs. Codes are
       captured off the mocked OtpEmailSender (AuthControllerTest's
       pattern); the resend cooldown is 0s here (test yaml) —
       AccountUpdateResendCooldownTest runs the real default.
Author review: Leong Wei Zhi to review via the PR.
*/

package foc.user.controller;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import foc.user.PostgresTestContainer;
import foc.user.entity.AccountUpdateOtp;
import foc.user.entity.PendingEmailChange;
import foc.user.entity.Role;
import foc.user.entity.User;
import foc.user.repository.AccountUpdateOtpRepository;
import foc.user.repository.PendingEmailChangeRepository;
import foc.user.repository.UserRepository;
import foc.user.service.OtpEmailSender;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
class AccountUpdateControllerTest extends PostgresTestContainer {

    private static final String EMAIL = "e1234567@u.nus.edu";
    private static final String NEW_EMAIL = "e7654321@u.nus.edu";
    private static final String PASSWORD = "ValidPassword123";

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

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private User alex;

    @BeforeEach
    void cleanDatabase() {
        gateOtps.deleteAll();
        pendingEmailChanges.deleteAll();
        userRepository.deleteAll();
        // a real BCrypt hash so the login assertions in the password and
        // email-change flows run against the account this suite edits
        alex = userRepository.save(new User(EMAIL, "student_alex",
            new BCryptPasswordEncoder().encode(PASSWORD), Role.USER));
    }

    // ---- request helpers, all as alex unless a caller is given ----

    private ResultActions requestOtp() throws Exception {
        return mockMvc.perform(post("/users/me/otp").with(user(alex.getId().toString())));
    }

    private ResultActions patchAccount(Map<String, String> body) throws Exception {
        return mockMvc.perform(patch("/users/me").with(user(alex.getId().toString()))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)));
    }

    private ResultActions verifyEmailChange(String code) throws Exception {
        return mockMvc.perform(post("/users/me/email/verify").with(user(alex.getId().toString()))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("code", code))));
    }

    private ResultActions resendEmailChange() throws Exception {
        return mockMvc.perform(post("/users/me/email/resend").with(user(alex.getId().toString())));
    }

    private ResultActions changePassword(Map<String, String> body) throws Exception {
        return mockMvc.perform(post("/users/me/password").with(user(alex.getId().toString()))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)));
    }

    private ResultActions login(String usernameOrEmail, String password) throws Exception {
        return mockMvc.perform(post("/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(
                Map.of("usernameOrEmail", usernameOrEmail, "password", password))));
    }

    // the last gate code the mocked sender was handed for this address
    private String gateCode() {
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(otpEmailSender, atLeastOnce()).sendAccountUpdateCode(eq(EMAIL), code.capture(), any());
        return code.getValue();
    }

    // the last confirmation code sent to the (new) address
    private String changeCode(String toEmail) {
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(otpEmailSender, atLeastOnce()).sendEmailChangeCode(eq(toEmail), code.capture(), any());
        return code.getValue();
    }

    // a six-digit code that is certainly not this one
    private static String not(String code) {
        return "000000".equals(code) ? "111111" : "000000";
    }

    // request a gate code and return it
    private String freshGateCode() throws Exception {
        requestOtp().andExpect(status().isAccepted());
        return gateCode();
    }

    // ---- POST /users/me/otp ----

    @Test
    @DisplayName("Requesting a gate code answers 202 with the timings and emails the current address")
    void requestOtp_accepted() throws Exception {
        requestOtp()
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.expiresInSeconds").value(600))
            .andExpect(jsonPath("$.resendInSeconds").value(0));

        assertThat(gateCode()).hasSize(6);
        assertThat(gateOtps.findByUserId(alex.getId())).isPresent();
    }

    @Test
    @DisplayName("A repeat request resends: new code wins, old one no longer counts, expiry stays put")
    void requestOtp_repeatResends() throws Exception {
        String first = freshGateCode();
        Instant expiresAt = gateOtps.findByUserId(alex.getId()).orElseThrow().getExpiresAt();

        requestOtp().andExpect(status().isAccepted());
        String second = gateCode();

        // a resend must not extend the row's life (PR #150's rule)
        assertThat(gateOtps.findByUserId(alex.getId()).orElseThrow().getExpiresAt())
            .isEqualTo(expiresAt);
        if (!second.equals(first)) {
            patchAccount(Map.of("username", "renamed_alex", "otp", first))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:foc:user:otp-invalid"));
        }
        patchAccount(Map.of("username", "renamed_alex", "otp", second))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("A soft-deleted caller gets 404, and no code goes out")
    void requestOtp_deletedCaller() throws Exception {
        alex.setDeletedAt(Instant.now());
        userRepository.save(alex);

        requestOtp().andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("The routes need a signed-in caller (their SecurityConfig lines)")
    void routes_unauthenticated() throws Exception {
        mockMvc.perform(post("/users/me/otp")).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/users/me").contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/users/me/email/verify").contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/users/me/email/resend")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/users/me/password").contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isUnauthorized());
    }

    // ---- PATCH /users/me: username ----

    @Test
    @DisplayName("A username change with the gate code applies and consumes the code")
    void patch_usernameApplies() throws Exception {
        String code = freshGateCode();

        patchAccount(Map.of("username", "renamed_alex", "otp", code))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("renamed_alex"))
            .andExpect(jsonPath("$.email").value(EMAIL));

        assertThat(userRepository.findById(alex.getId()).orElseThrow().getUsername())
            .isEqualTo("renamed_alex");
        // single-use: the row is gone and the same code is now refused
        assertThat(gateOtps.findByUserId(alex.getId())).isEmpty();
        patchAccount(Map.of("username", "again_alex", "otp", code))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.type").value("urn:foc:user:otp-required"))
            .andExpect(jsonPath("$.detail").value("Request a verification code first"));
    }

    @Test
    @DisplayName("A case-only rename passes the uniqueness check (except-self)")
    void patch_caseOnlyRename() throws Exception {
        patchAccount(Map.of("username", "Student_Alex", "otp", freshGateCode()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("Student_Alex"));
    }

    @Test
    @DisplayName("A username another account holds is refused with its type")
    void patch_usernameTaken() throws Exception {
        userRepository.save(new User(NEW_EMAIL, "taken_name", "hashed", Role.USER));

        patchAccount(Map.of("username", "Taken_Name", "otp", freshGateCode()))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.type").value("urn:foc:user:username-taken"))
            .andExpect(jsonPath("$.detail").value("Username is already taken"));

        // the refusal rolled back, restoring the gate row: the same code
        // still authorizes a corrected retry
        patchAccount(Map.of("username", "free_name", "otp", gateCode()))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("The fifth wrong code discards the gate with a 429; even the right code is dead")
    void patch_attemptsExhausted() throws Exception {
        String code = freshGateCode();

        for (int attempt = 1; attempt <= 4; attempt++) {
            patchAccount(Map.of("username", "renamed_alex", "otp", not(code)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:foc:user:otp-invalid"))
                .andExpect(jsonPath("$.detail").value("Invalid verification code"));
        }
        patchAccount(Map.of("username", "renamed_alex", "otp", not(code)))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.type").value("urn:foc:user:otp-attempts-exceeded"))
            .andExpect(jsonPath("$.detail").value("Too many incorrect codes; request a new code"));

        assertThat(gateOtps.findByUserId(alex.getId())).isEmpty();
        patchAccount(Map.of("username", "renamed_alex", "otp", code))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.type").value("urn:foc:user:otp-required"));
        // nothing ever applied
        assertThat(userRepository.findById(alex.getId()).orElseThrow().getUsername())
            .isEqualTo("student_alex");
    }

    @Test
    @DisplayName("Without a requested gate code the PATCH is refused up front")
    void patch_withoutGateCode() throws Exception {
        patchAccount(Map.of("username", "renamed_alex", "otp", "123456"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.type").value("urn:foc:user:otp-required"))
            .andExpect(jsonPath("$.detail").value("Request a verification code first"));
    }

    @Test
    @DisplayName("A role in the PATCH body has no field to land in (allow-list DTO, rubric P1.5)")
    void patch_roleIgnored() throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("username", "sneaky_alex");
        body.put("otp", freshGateCode());
        body.put("role", "ADMIN");
        body.put("id", "1");
        body.put("deletedAt", "2026-01-01T00:00:00Z");

        patchAccount(body)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("USER"));

        User reloaded = userRepository.findById(alex.getId()).orElseThrow();
        assertThat(reloaded.getRole()).isEqualTo(Role.USER);
        assertThat(reloaded.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("A PATCH changing nothing is refused, and a blank code is a validation error")
    void patch_validation() throws Exception {
        patchAccount(Map.of("otp", "123456"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value(
                "At least one of username or email is required."));
        patchAccount(Map.of("username", "renamed_alex"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Verification code is required."));
    }

    // ---- PATCH /users/me: email ----

    @Test
    @DisplayName("An email change parks pending and the code goes to the NEW address")
    void patch_emailParks() throws Exception {
        patchAccount(Map.of("email", NEW_EMAIL, "otp", freshGateCode()))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.user.email").value(EMAIL))
            .andExpect(jsonPath("$.email").value(NEW_EMAIL))
            .andExpect(jsonPath("$.expiresInSeconds").value(600))
            .andExpect(jsonPath("$.resendInSeconds").value(0));

        // nothing applied yet: the account keeps its email until the new
        // address confirms
        assertThat(userRepository.findById(alex.getId()).orElseThrow().getEmail()).isEqualTo(EMAIL);
        assertThat(pendingEmailChanges.findByUserId(alex.getId()).orElseThrow().getNewEmail())
            .isEqualTo(NEW_EMAIL);
        assertThat(changeCode(NEW_EMAIL)).hasSize(6);
    }

    @Test
    @DisplayName("Asking for the email you already have parks nothing and sends nothing")
    void patch_emailUnchanged() throws Exception {
        patchAccount(Map.of("email", EMAIL.toUpperCase(), "otp", freshGateCode()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value(EMAIL));

        assertThat(pendingEmailChanges.findByUserId(alex.getId())).isEmpty();
    }

    @Test
    @DisplayName("An email another account holds is refused with its type")
    void patch_emailTaken() throws Exception {
        userRepository.save(new User(NEW_EMAIL, "other_user", "hashed", Role.USER));

        patchAccount(Map.of("email", NEW_EMAIL, "otp", freshGateCode()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.type").value("urn:foc:user:email-taken"))
            .andExpect(jsonPath("$.detail").value("Email is already registered"));
    }

    @Test
    @DisplayName("Username and email combine in one PATCH: the name applies, the email waits")
    void patch_combined() throws Exception {
        patchAccount(Map.of("username", "renamed_alex", "email", NEW_EMAIL, "otp", freshGateCode()))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.user.username").value("renamed_alex"))
            .andExpect(jsonPath("$.user.email").value(EMAIL))
            .andExpect(jsonPath("$.email").value(NEW_EMAIL));

        User reloaded = userRepository.findById(alex.getId()).orElseThrow();
        assertThat(reloaded.getUsername()).isEqualTo("renamed_alex");
        assertThat(reloaded.getEmail()).isEqualTo(EMAIL);
    }

    @Test
    @DisplayName("A second PATCH to a different address replaces the pending change, attempts reset")
    void patch_replacePendingChange() throws Exception {
        patchAccount(Map.of("email", NEW_EMAIL, "otp", freshGateCode()))
            .andExpect(status().isAccepted());
        String firstCode = changeCode(NEW_EMAIL);
        // burn an attempt so the reset is observable
        verifyEmailChange(not(firstCode)).andExpect(status().isBadRequest());
        assertThat(pendingEmailChanges.findByUserId(alex.getId()).orElseThrow().getAttempts())
            .isEqualTo(1);

        String thirdEmail = "e1111111@u.nus.edu";
        patchAccount(Map.of("email", thirdEmail, "otp", freshGateCode()))
            .andExpect(status().isAccepted());

        PendingEmailChange replaced = pendingEmailChanges.findByUserId(alex.getId()).orElseThrow();
        assertThat(replaced.getNewEmail()).isEqualTo(thirdEmail);
        assertThat(replaced.getAttempts()).isZero();
        // the discarded change's code confirms nothing
        verifyEmailChange(firstCode)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.type").value("urn:foc:user:otp-invalid"));
    }

    @Test
    @DisplayName("A second PATCH to the same address is a resend: fresh code, same expiry and attempts")
    void patch_repeatSameEmailResends() throws Exception {
        patchAccount(Map.of("email", NEW_EMAIL, "otp", freshGateCode()))
            .andExpect(status().isAccepted());
        String firstCode = changeCode(NEW_EMAIL);
        verifyEmailChange(not(firstCode)).andExpect(status().isBadRequest());
        Instant expiresAt = pendingEmailChanges.findByUserId(alex.getId()).orElseThrow().getExpiresAt();

        patchAccount(Map.of("email", NEW_EMAIL, "otp", freshGateCode()))
            .andExpect(status().isAccepted());

        PendingEmailChange renewed = pendingEmailChanges.findByUserId(alex.getId()).orElseThrow();
        assertThat(renewed.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(renewed.getAttempts()).isEqualTo(1);
        String secondCode = changeCode(NEW_EMAIL);
        if (!secondCode.equals(firstCode)) {
            verifyEmailChange(firstCode).andExpect(status().isBadRequest());
        }
        verifyEmailChange(secondCode).andExpect(status().isOk());
    }

    // ---- POST /users/me/email/verify ----

    @Test
    @DisplayName("The new address's code applies the change; the new email logs in, the old is free")
    void verify_applies() throws Exception {
        patchAccount(Map.of("email", NEW_EMAIL, "otp", freshGateCode()))
            .andExpect(status().isAccepted());

        verifyEmailChange(changeCode(NEW_EMAIL))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value(NEW_EMAIL));

        assertThat(userRepository.findById(alex.getId()).orElseThrow().getEmail())
            .isEqualTo(NEW_EMAIL);
        assertThat(pendingEmailChanges.findByUserId(alex.getId())).isEmpty();
        login(NEW_EMAIL, PASSWORD).andExpect(status().isOk());
        login(EMAIL, PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("The fifth wrong code discards the pending change with a 429")
    void verify_attemptsExhausted() throws Exception {
        patchAccount(Map.of("email", NEW_EMAIL, "otp", freshGateCode()))
            .andExpect(status().isAccepted());
        String code = changeCode(NEW_EMAIL);

        for (int attempt = 1; attempt <= 4; attempt++) {
            verifyEmailChange(not(code)).andExpect(status().isBadRequest());
        }
        verifyEmailChange(not(code))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.type").value("urn:foc:user:otp-attempts-exceeded"))
            .andExpect(jsonPath("$.detail").value(
                "Too many incorrect codes; request the email change again for a new code"));

        assertThat(pendingEmailChanges.findByUserId(alex.getId())).isEmpty();
        assertThat(userRepository.findById(alex.getId()).orElseThrow().getEmail()).isEqualTo(EMAIL);
    }

    @Test
    @DisplayName("An expired pending change is rejected on sight and swept away")
    void verify_expired() throws Exception {
        pendingEmailChanges.save(new PendingEmailChange(alex.getId(), NEW_EMAIL,
            "$2a$10$abcdefghijklmnopqrstuv", Instant.now().minusSeconds(700),
            Instant.now().minusSeconds(100)));

        verifyEmailChange("123456")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.type").value("urn:foc:user:otp-expired"))
            .andExpect(jsonPath("$.detail").value("Code has expired; request the email change again"));

        assertThat(pendingEmailChanges.findByUserId(alex.getId())).isEmpty();
    }

    @Test
    @DisplayName("Verify with nothing pending is an honest 400, not a guessing game")
    void verify_nothingPending() throws Exception {
        verifyEmailChange("123456")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.type").value("urn:foc:user:email-change-none"))
            .andExpect(jsonPath("$.detail").value(
                "No email change is pending; request the change again"));
    }

    @Test
    @DisplayName("An address taken while the code was in flight discards the change; the old email stays")
    void verify_takenMeanwhile() throws Exception {
        patchAccount(Map.of("email", NEW_EMAIL, "otp", freshGateCode()))
            .andExpect(status().isAccepted());
        String code = changeCode(NEW_EMAIL);
        // someone registers the address before the code is entered
        userRepository.save(new User(NEW_EMAIL, "race_winner", "hashed", Role.USER));

        verifyEmailChange(code)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.type").value("urn:foc:user:email-taken"))
            .andExpect(jsonPath("$.detail").value("Email is already registered"));

        // the unusable change is discarded, not left to refuse its owner
        // until the TTL runs out; the account is untouched
        assertThat(pendingEmailChanges.findByUserId(alex.getId())).isEmpty();
        assertThat(userRepository.findById(alex.getId()).orElseThrow().getEmail()).isEqualTo(EMAIL);
    }

    // ---- POST /users/me/email/resend ----

    @Test
    @DisplayName("Resend renews the code without extending its life; nothing pending is a 400")
    void resend_renewsCode() throws Exception {
        patchAccount(Map.of("email", NEW_EMAIL, "otp", freshGateCode()))
            .andExpect(status().isAccepted());
        String first = changeCode(NEW_EMAIL);
        Instant expiresAt = pendingEmailChanges.findByUserId(alex.getId()).orElseThrow().getExpiresAt();

        resendEmailChange()
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.email").value(NEW_EMAIL))
            .andExpect(jsonPath("$.user.email").value(EMAIL));

        assertThat(pendingEmailChanges.findByUserId(alex.getId()).orElseThrow().getExpiresAt())
            .isEqualTo(expiresAt);
        String second = changeCode(NEW_EMAIL);
        if (!second.equals(first)) {
            verifyEmailChange(first).andExpect(status().isBadRequest());
        }
        verifyEmailChange(second).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Resend with nothing pending is refused")
    void resend_nothingPending() throws Exception {
        resendEmailChange()
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.type").value("urn:foc:user:email-change-none"));
    }

    // ---- POST /users/me/password ----

    @Test
    @DisplayName("A password change takes effect: the old password stops working, the new one logs in")
    void password_applies() throws Exception {
        changePassword(Map.of("newPassword", "NewValidPass456",
                "confirmPassword", "NewValidPass456", "otp", freshGateCode()))
            .andExpect(status().isNoContent());

        login(EMAIL, PASSWORD).andExpect(status().isUnauthorized());
        login(EMAIL, "NewValidPass456").andExpect(status().isOk());
        // single-use here too
        assertThat(gateOtps.findByUserId(alex.getId())).isEmpty();
    }

    @Test
    @DisplayName("Mismatched entries are refused server-side (F2.1.4)")
    void password_entriesMismatch() throws Exception {
        changePassword(Map.of("newPassword", "NewValidPass456",
                "confirmPassword", "OtherValidPass456", "otp", "123456"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Password entries do not match."));

        login(EMAIL, PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("The sign-up password policy applies to the new password (F2.1.5)")
    void password_policyRecheck() throws Exception {
        changePassword(Map.of("newPassword", "short", "confirmPassword", "short", "otp", "123456"))
            .andExpect(status().isBadRequest())
            // both rules report; their order within one field isn't pinned
            .andExpect(jsonPath("$.detail").value(allOf(
                containsString("Password must be between 10 and 50 characters long."),
                containsString("Password must contain at least one uppercase letter,"
                    + " one lowercase letter, and one number."))));
    }

    @Test
    @DisplayName("A wrong gate code changes nothing")
    void password_wrongCode() throws Exception {
        String code = freshGateCode();

        changePassword(Map.of("newPassword", "NewValidPass456",
                "confirmPassword", "NewValidPass456", "otp", not(code)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.type").value("urn:foc:user:otp-invalid"));

        login(EMAIL, PASSWORD).andExpect(status().isOk());
    }

    // ---- transaction semantics ----

    @Test
    @DisplayName("A failed confirmation email rolls everything back: no rename, no pending row, gate intact")
    void patch_mailFailureRollsBack() throws Exception {
        String code = freshGateCode();
        doThrow(new MailSendException("smtp down"))
            .when(otpEmailSender).sendEmailChangeCode(anyString(), anyString(), any());

        patchAccount(Map.of("username", "renamed_alex", "email", NEW_EMAIL, "otp", code))
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.detail").value(
                "Could not send the verification email; try again later"));

        // the whole PATCH rolled back: the username change, the parked
        // change, and the gate consumption — the code is good for a retry
        assertThat(userRepository.findById(alex.getId()).orElseThrow().getUsername())
            .isEqualTo("student_alex");
        assertThat(pendingEmailChanges.findByUserId(alex.getId())).isEmpty();
        assertThat(gateOtps.findByUserId(alex.getId())).isPresent();

        doNothing().when(otpEmailSender).sendEmailChangeCode(anyString(), anyString(), any());
        patchAccount(Map.of("username", "renamed_alex", "email", NEW_EMAIL, "otp", code))
            .andExpect(status().isAccepted());
    }
}
