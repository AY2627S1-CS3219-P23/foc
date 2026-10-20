/*
AI Assistance Disclosure:
2026-09-22 (Claude, Sonnet 4.6) Generated test code to test controller layer. Generated code included set up of testcontainers running Postgres.
       Additional cases added for blank fields, password character class violations, and invalid username characters.
Author review: Ryan validated correctness and naming.
2026-09-23 (Claude, Sonnet 4.6): setup token supplied on existing cases; added
       missing-token and wrong-token 403 cases.
2026-09-23 (Claude Code, Opus 5.5): file renamed from OwnerSetUpControllerTest.java;
       moved to Jackson 3 / Testcontainers 2.x; added cases for mixed-case and
       padded email, the password length message, concurrent setup requests,
       and an unauthenticated /actuator/health (PR #126 review).
2026-09-23 (Claude Code, Fable 5), issue #86: countByRole calls switched to the
       Role enum following the entity's String-to-enum conversion.
2026-09-25 (Claude Code, Opus 5.5): container moved to the shared
       PostgresTestContainer base (PR #131 review).
2026-09-25 (Claude Code, Opus 5.5): owner setup no longer rejects a second
       owner; the second-call case now expects another OWNER, and the concurrency
       case uses the same email to exercise the setup lock. Test names
       follow the setupFirstOwner -> setupOwner rename.
2026-09-25 (Claude Code, Opus 5.5), PR #132 review: concurrent setups with
       different emails must both succeed; the concurrent-request code is
       shared by both concurrency cases.
2026-09-26 (Claude Code, Opus 5.5), issue #96: owner counts taken from
       findAll, as UserRepository.countByRole was removed.
2026-09-27 (Claude Code, Fable 5), issue #93: reuse-block cases added — a
       soft-deleted account's email and username still fail the uniqueness
       checks (design doc §2: 30-day reuse block).
2026-09-29 (Claude Code, Opus 5.5), issue #147: an invalid body now gets
       problem+json with the broken rule's message (ProblemDetailAdvice,
       team decision).
2026-10-02 (Claude Code, Opus 5.5), issue #154: the wrong-token 403 is
       checked for a problem+json body.
2026-10-19: Parts copied from AuthControllerTest, which was AI-assisted
*/


package foc.user.controller;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import foc.user.PostgresTestContainer;
import foc.user.dto.SignupOwnerRequest;
import foc.user.entity.PendingSignup;
import foc.user.entity.Role;
import foc.user.entity.User;
import foc.user.repository.PendingSignupRepository;
import foc.user.repository.UserRepository;
import foc.user.service.OtpEmailSender;
import foc.user.service.OwnerSignupService;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
class OwnerSignupControllerTest extends PostgresTestContainer {

    private static final String CORRECT_SETUP_TOKEN = "test-value";
    private static final String PASSWORD = "ValidPassword123";
    
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PendingSignupRepository pendingSignupRepository;

    @Autowired
    private OwnerSignupService ownerSignupService;

    // captures the plain code instead of speaking SMTP; reset per test
    @MockitoBean
    private OtpEmailSender otpEmailSender;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @BeforeEach
    void cleanDatabase() {
        pendingSignupRepository.deleteAll();
        userRepository.deleteAll();
        ReflectionTestUtils.setField(ownerSignupService, "expectedOwnerKey", "test-value");
    }

    private ResultActions postJson(String path, Map<String, String> body) throws Exception {
        return mockMvc.perform(post(path)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)));
    }

    private ResultActions signup(String email, String username, String password, String ownerKey) throws Exception {
        return postJson("/auth/signup-owner", Map.of("email", email, "username", username, "password", password, "ownerKey", ownerKey));
    }

    private ResultActions verifySignup(String email, String code) throws Exception {
        return postJson("/auth/signup/verify-owner", Map.of("email", email, "code", code));
    }

    private void createOwner(String email, String username) throws Exception {
        signup(email, username, PASSWORD, CORRECT_SETUP_TOKEN).andExpect(status().isAccepted());
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(otpEmailSender, atLeastOnce()).sendSignupCode(eq(email), code.capture(), any(), eq(true));
        verifySignup(email, code.getValue()).andExpect(status().isCreated());
    }
    
    private String emailedCode(String email) {
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(otpEmailSender, atLeastOnce()).sendSignupCode(eq(email), code.capture(), any(), eq(true));
        return code.getValue();
    }
    // a six-digit code that is certainly not the live one
    private String notTheCode(String email) {
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(otpEmailSender, atLeastOnce()).sendSignupCode(eq(email), code.capture(), any(), eq(true));
        
        return "000000".equals(emailedCode(email)) ? "111111" : "000000";
    }

    @Test
    @DisplayName("Sign-up answers 202 with a pending row and an emailed code — no account yet")
    void signup_accepted() throws Exception {
        signup("e1234567@u.nus.edu", "student_alex", PASSWORD, CORRECT_SETUP_TOKEN)
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.email").value("e1234567@u.nus.edu"))
            .andExpect(jsonPath("$.expiresInSeconds").value(600))
            // the cooldown is off in this file's config, so the dialog is
            // told it may resend at once (SignupResendCooldownTest runs 60s)
            .andExpect(jsonPath("$.resendInSeconds").value(0));

        assertThat(userRepository.findByUsernameIgnoreCase("student_alex")).isEmpty();
        PendingSignup pending = pendingSignupRepository.findByEmail("e1234567@u.nus.edu").orElseThrow();
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(otpEmailSender, atLeastOnce()).sendSignupCode(eq("e1234567@u.nus.edu"), code.capture(), any(), eq(true));
        
        // both secrets are stored hashed, never plain
        assertThat(pending.getPasswordHash()).isNotEqualTo(PASSWORD).startsWith("$2");
        assertThat(pending.getCodeHash()).isNotEqualTo(code.getValue()).startsWith("$2");
    }

    @Test
    @DisplayName("Verifying the emailed code creates the account")
    void signup_verifyFlow() throws Exception {
        signup("e1234567@u.nus.edu", "student_alex", PASSWORD, CORRECT_SETUP_TOKEN)
            .andExpect(status().isAccepted());

        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(otpEmailSender, atLeastOnce()).sendSignupCode(eq("e1234567@u.nus.edu"), code.capture(), any(), eq(true));
        
        verifySignup("e1234567@u.nus.edu", code.getValue())
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.email").value("e1234567@u.nus.edu"))
            .andExpect(jsonPath("$.username").value("student_alex"))
            .andExpect(jsonPath("$.role").value("OWNER"))
            .andExpect(jsonPath("$.password").doesNotExist())
            .andExpect(jsonPath("$.passwordHash").doesNotExist());

        User saved = userRepository.findByUsernameIgnoreCase("student_alex").orElseThrow();
        assertThat(saved.getPasswordHash()).isNotEqualTo(PASSWORD).startsWith("$2");
        assertThat(saved.getRole()).isEqualTo(Role.OWNER);
        assertThat(pendingSignupRepository.findByEmail("e1234567@u.nus.edu")).isEmpty();
    }
    
    @Test
    @DisplayName("Should allow a second setup call to create another OWNER")
    void setupOwner_secondCallCreatesAnotherOwner() throws Exception {
        createOwner("e1234567@u.nus.edu", "student_alex");
        createOwner("e1234568@u.nus.edu", "student_alexy");
        assertThat(userRepository.findByUsernameIgnoreCase("student_alex").orElseThrow()
            .getRole()).isEqualTo(Role.OWNER);
        assertThat(userRepository.findByUsernameIgnoreCase("student_alexy").orElseThrow()
            .getRole()).isEqualTo(Role.OWNER);
    }
    
    @Test
    @DisplayName("No server-side owner key should throw 503")
    void setupOwner_noSetupKey() throws Exception {
        ReflectionTestUtils.setField(ownerSignupService, "expectedOwnerKey", "");
        signup("e1234567@u.nus.edu", "student_alex", PASSWORD, CORRECT_SETUP_TOKEN)
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.type").value("urn:foc:user:owner-setup-token-missing"))
            .andExpect(jsonPath("$.detail").value("Owner setup is unavailable"));
    }

    @Test
    @DisplayName("No signup token creates regular user instead, notified by email")
    void setupOwner_missingSetupToken() throws Exception {
        signup("e1234567@u.nus.edu", "student_alex", PASSWORD, "")
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.email").value("e1234567@u.nus.edu"))
            .andExpect(jsonPath("$.expiresInSeconds").value(600))
            .andExpect(jsonPath("$.resendInSeconds").value(0));

        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(otpEmailSender, atLeastOnce()).sendSignupCode(eq("e1234567@u.nus.edu"), code.capture(), any(), eq(false));
        
        verifySignup("e1234567@u.nus.edu", code.getValue())
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.email").value("e1234567@u.nus.edu"))
            .andExpect(jsonPath("$.username").value("student_alex"))
            .andExpect(jsonPath("$.role").value("USER"))
            .andExpect(jsonPath("$.password").doesNotExist())
            .andExpect(jsonPath("$.passwordHash").doesNotExist());

        User saved = userRepository.findByUsernameIgnoreCase("student_alex").orElseThrow();
        assertThat(saved.getPasswordHash()).isNotEqualTo(PASSWORD).startsWith("$2");
        assertThat(saved.getRole().equals(Role.USER));
        assertThat(pendingSignupRepository.findByEmail("e1234567@u.nus.edu")).isEmpty();
    }

    @Test
    @DisplayName("Wrong signup token creates user instead, notified by email")
    void setupOwner_wrongSetupToken() throws Exception {
                signup("e1234567@u.nus.edu", "student_alex", PASSWORD, "wrongkey")
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.email").value("e1234567@u.nus.edu"))
            .andExpect(jsonPath("$.expiresInSeconds").value(600))
            .andExpect(jsonPath("$.resendInSeconds").value(0));

        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(otpEmailSender, atLeastOnce()).sendSignupCode(eq("e1234567@u.nus.edu"), code.capture(), any(), eq(false));
        
        verifySignup("e1234567@u.nus.edu", code.getValue())
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.email").value("e1234567@u.nus.edu"))
            .andExpect(jsonPath("$.username").value("student_alex"))
            .andExpect(jsonPath("$.role").value("USER"))
            .andExpect(jsonPath("$.password").doesNotExist())
            .andExpect(jsonPath("$.passwordHash").doesNotExist());

        User saved = userRepository.findByUsernameIgnoreCase("student_alex").orElseThrow();
        assertThat(saved.getPasswordHash()).isNotEqualTo(PASSWORD).startsWith("$2");
        assertThat(saved.getRole().equals(Role.USER));
        assertThat(pendingSignupRepository.findByEmail("e1234567@u.nus.edu")).isEmpty();    }

    // Remaining tests found in AuthControllerTest

    @Test
    @DisplayName("A wrong otp code is 400; the right one still works after it")
    void signup_wrongCodeThenRight() throws Exception {
        signup("e1234567@u.nus.edu", "student_alex", PASSWORD, CORRECT_SETUP_TOKEN).andExpect(status().isAccepted());
        String code = emailedCode("e1234567@u.nus.edu");
        String wrong = code.equals("000000") ? "000001" : "000000";

        verifySignup("e1234567@u.nus.edu", wrong)
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.type").value("urn:foc:user:otp-invalid"))
            .andExpect(jsonPath("$.detail").value("Invalid verification code"));

        verifySignup("e1234567@u.nus.edu", code).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("The fifth wrong otp code spends the pending sign-up with a 429; re-signing up recovers")
    void signup_attemptsExhausted() throws Exception {
        signup("e1234567@u.nus.edu", "student_alex", PASSWORD, CORRECT_SETUP_TOKEN).andExpect(status().isAccepted());
        String code = emailedCode("e1234567@u.nus.edu");
        String wrong = code.equals("000000") ? "000001" : "000000";

        for (int attempt = 1; attempt <= 4; attempt++) {
            verifySignup("e1234567@u.nus.edu", wrong).andExpect(status().isBadRequest());
        }
        verifySignup("e1234567@u.nus.edu", wrong)
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.type").value("urn:foc:user:otp-attempts-exceeded"))
            .andExpect(header().string(HttpHeaders.RETRY_AFTER, "0"))
            .andExpect(jsonPath("$.detail").value(
                "Too many incorrect codes; sign up again to get a new code"));

        // spent row exists and returns bad verification code
        assertThat(pendingSignupRepository.findByEmail("e1234567@u.nus.edu")).isPresent();
        verifySignup("e1234567@u.nus.edu", code)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Invalid verification code"));

        // needs same details to reuse row
        createOwner("e1234567@u.nus.edu", "student_alex");
    }

    @Test
    @DisplayName("A repeat of the same request resends: the new code wins, the old one no longer counts")
    void signup_repeatResends() throws Exception {
        signup("e1234567@u.nus.edu", "student_alex", PASSWORD, CORRECT_SETUP_TOKEN).andExpect(status().isAccepted());
        String first = emailedCode("e1234567@u.nus.edu");

        signup("e1234567@u.nus.edu", "student_alex", PASSWORD, CORRECT_SETUP_TOKEN).andExpect(status().isAccepted());
        String second = emailedCode("e1234567@u.nus.edu");

        if (!second.equals(first)) {
            verifySignup("e1234567@u.nus.edu", first)
                .andExpect(status().isBadRequest());
        }
        verifySignup("e1234567@u.nus.edu", second).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("A repeat with other details is a 409, and the account stays the first request's")
    void signup_repeatWithOtherDetailsIsConflict() throws Exception {
        signup("e1234567@u.nus.edu", "student_alex", PASSWORD, CORRECT_SETUP_TOKEN).andExpect(status().isAccepted());

        // the hijack: someone else pending the same email with their own
        // password used to rewrite the row, so the code the student
        // received created an account holding that password
        signup("e1234567@u.nus.edu", "attacker_x", "AttackerPass123", CORRECT_SETUP_TOKEN)
            .andExpect(status().isConflict())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.detail").value("A sign-up for this email is already in progress;"
                + " check your inbox for the code, or try again once it expires"));

        // the first request's code still works, and makes the first
        // request's account: its username, and its password logs in
        verifySignup("e1234567@u.nus.edu", emailedCode("e1234567@u.nus.edu"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.username").value("student_alex"));
    }

    @Test
    @DisplayName("Attempts survive a resend: the fifth wrong code across two codes still exhausts")
    void signup_attemptsSurviveResend() throws Exception {
        signup("e1234567@u.nus.edu", "student_alex", PASSWORD, CORRECT_SETUP_TOKEN).andExpect(status().isAccepted());

        for (int i = 0; i < 4; i++) {
            verifySignup("e1234567@u.nus.edu", notTheCode("e1234567@u.nus.edu"))
                .andExpect(status().isBadRequest());
        }
        // a fresh code must not hand out a fresh attempt budget, or the
        // limit would cap guesses per code instead of per sign-up
        signup("e1234567@u.nus.edu", "student_alex", PASSWORD, CORRECT_SETUP_TOKEN).andExpect(status().isAccepted());

        verifySignup("e1234567@u.nus.edu", notTheCode("e1234567@u.nus.edu"))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.detail").value(
                "Too many incorrect codes; sign up again to get a new code"));
        // kept as spent, not deleted, so the cooldown still gates the
        // next code (PR #157 review, @Sinnez1)
        assertThat(pendingSignupRepository.findByEmail("e1234567@u.nus.edu")).isPresent();
    }

    @Test
    @DisplayName("Verify with no pending sign-up answers like a wrong code")
    void signup_verifyWithoutSignup() throws Exception {
        verifySignup("e1234567@u.nus.edu", "123456")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.type").value("urn:foc:user:otp-invalid"))
            .andExpect(jsonPath("$.detail").value("Invalid verification code"));
    }
    
    @Test
    @DisplayName("Sign-up with a non-NUS email is 400 with the exact reason")
    void signup_invalidEmail() throws Exception {
        signup("alex@gmail.com", "student_alex", PASSWORD, CORRECT_SETUP_TOKEN)
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.detail").value(
                "Email must be a valid @u.nus.edu address. Email used should not be the friendly email."));
    }

    @Test
    @DisplayName("Sign-up with a taken email or username is 400 with the exact reason")
    void signup_duplicates() throws Exception {
        createOwner("e1234567@u.nus.edu", "student_alex");

        signup("E1234567@u.nus.edu", "someone_else", PASSWORD, CORRECT_SETUP_TOKEN)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Email is already registered"));
        signup("e7654321@u.nus.edu", "student_alex", PASSWORD, CORRECT_SETUP_TOKEN)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Username is already taken"));
        // usernames are unique ignoring case
        signup("e7654321@u.nus.edu", "Student_Alex", PASSWORD, CORRECT_SETUP_TOKEN)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Username is already taken"));
    }

    @Test
    @DisplayName("An identifier taken while a sign-up was pending fails its verify and frees the email")
    void signup_identifierTakenWhilePending() throws Exception {
        signup("e1234567@u.nus.edu", "student_alex", PASSWORD, CORRECT_SETUP_TOKEN).andExpect(status().isAccepted());
        String code = emailedCode("e1234567@u.nus.edu");
        // someone else completes the same username first
        createOwner("e7654321@u.nus.edu", "student_alex");

        verifySignup("e1234567@u.nus.edu", code)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Username is already taken"));

        // losing that race must not cost the loser their email for the
        // rest of the TTL (PR #150 re-review): the pending row is gone, so
        // a new name works straight away instead of answering 409
        assertThat(pendingSignupRepository.findByEmail("e1234567@u.nus.edu")).isEmpty();
        signup("e1234567@u.nus.edu", "student_alex_2", PASSWORD, CORRECT_SETUP_TOKEN).andExpect(status().isAccepted());
        verifySignup("e1234567@u.nus.edu", emailedCode("e1234567@u.nus.edu"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.username").value("student_alex_2"));
    }

    @Test
    @DisplayName("Sign-up ignores a role in the body")
    void signup_roleIgnored() throws Exception {
        postJson("/auth/signup-owner", Map.of(
                "email", "e1234567@u.nus.edu",
                "username", "student_alex",
                "password", PASSWORD,
                "ownerKey", "Wrongtoken",
                "role", "OWNER"))
            .andExpect(status().isAccepted());
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(otpEmailSender, atLeastOnce()).sendSignupCode(eq("e1234567@u.nus.edu"), code.capture(), any(), eq(false));
        verifySignup("e1234567@u.nus.edu", code.getValue())
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.role").value("USER"));

        assertThat(userRepository.findByUsernameIgnoreCase("student_alex").orElseThrow()
            .getRole()).isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("Several broken rules are all reported, one sentence each")
    void signup_severalReasons() throws Exception {
        signup("", "", PASSWORD, CORRECT_SETUP_TOKEN)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value(allOf(
                containsString("Email is required."),
                containsString("Username is required."))));
    }

    @Test
    @DisplayName("Sign-up with no body is 400")
    void signup_noBody() throws Exception {
        mockMvc.perform(post("/auth/signup-owner").contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Request body is missing or malformed"));
    }

    @Test
    @DisplayName("A body that isn't JSON is 415 problem+json")
    void unsupportedContentType() throws Exception {
        mockMvc.perform(post("/auth/signup-owner").contentType(MediaType.TEXT_PLAIN).content("student_alex"))
            .andExpect(status().isUnsupportedMediaType())
            .andExpect(header().string("Accept", containsString("application/json")))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(415))
            .andExpect(jsonPath("$.detail").value("Content-Type 'text/plain' is not supported."));
    }

    @Test
    @DisplayName("A method the path doesn't take is 405 problem+json")
    void unsupportedMethod() throws Exception {
        mockMvc.perform(get("/auth/signup-owner"))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(header().string("Allow", containsString("POST")))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(405))
            .andExpect(jsonPath("$.detail").value("Method 'GET' is not supported."));
    }

    // 30-day reuse block (issue #93): a soft-deleted account keeps its row,
    // so its email and username stay reserved until the day-31 purge
    @Test
    @DisplayName("Should reject a soft-deleted account's email with 400 Bad Request")
    void setupOwner_softDeletedEmailStaysReserved() throws Exception {
        saveSoftDeletedUser("e1234567@u.nus.edu", "gone_user");
        signup("e1234567@u.nus.edu", "student_alex", PASSWORD, CORRECT_SETUP_TOKEN)
            .andExpect(status().isBadRequest());    
    }

    @Test
    @DisplayName("Should reject a soft-deleted account's username with 400 Bad Request")
    void setupOwner_softDeletedUsernameStaysReserved() throws Exception {
        saveSoftDeletedUser("e1234567@u.nus.edu", "gone_user");
        signup("e1234568@u.nus.edu", "gone_user", PASSWORD, CORRECT_SETUP_TOKEN)
            .andExpect(status().isBadRequest());
    }

    private void saveSoftDeletedUser(String email, String username) {
        User deleted = new User(email, username, "hashed_password", Role.USER);
        deleted.softDelete(Instant.now());
        userRepository.save(deleted);
    }

    @Test
    @DisplayName("Should reject blank email with 400 Bad Request")
    void setupOwner_blankEmail() throws Exception {
        signup("e1234567@u.nus.edu", "student_alex", "", CORRECT_SETUP_TOKEN)
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should reject password with no uppercase letter with 400 Bad Request")
    void setupOwner_passwordMissingUppercase() throws Exception {
        signup("e1234567@u.nus.edu", "student_alex", "lowercasepw1!", CORRECT_SETUP_TOKEN)
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should reject password with no digit with 400 Bad Request")
    void setupOwner_passwordMissingDigit() throws Exception {
        signup("e1234567@u.nus.edu", "student_alex", "NoDigitPasswordOnly!", CORRECT_SETUP_TOKEN)
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should reject username with special characters with 400 Bad Request")
    void setupOwner_invalidUsernameCharacters() throws Exception {
        // hyphens and dash not allowed
        signup("e1234567@u.nus.edu", "owner-user!", "ValidPassword123!", CORRECT_SETUP_TOKEN)
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should accept mixed-case, padded NUS email and store it normalised")
    void setupOwner_mixedCasePaddedEmail() throws Exception {
        signup(" E1234567@U.NUS.EDU ", "student_alex", PASSWORD, CORRECT_SETUP_TOKEN)
            .andExpect(status().isAccepted());
        verifySignup("e1234567@u.nus.edu", emailedCode("e1234567@u.nus.edu"))
            .andExpect(status().isCreated());
        assertThat(userRepository.findByUsernameIgnoreCase("student_alex").orElseThrow()
            .getEmail()).isEqualTo("e1234567@u.nus.edu");
    }

    @Test
    @DisplayName("Should reject over-long password (> 50 chars) with 400 Bad Request")
    void setupOwner_longPassword() throws Exception {
        signup("e1234567@u.nus.edu", "owner-user!", "Aa1" + "x".repeat(60), CORRECT_SETUP_TOKEN)
            .andExpect(status().isBadRequest());
    }

    // sends both requests at the same moment and returns their status codes
    private List<Integer> sendConcurrently(SignupOwnerRequest first, SignupOwnerRequest second) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (SignupOwnerRequest request : List.of(first, second)) {
                Callable<Integer> call = () -> {
                    start.await();
                    return mockMvc.perform(post("/auth/signup-owner")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                        .andReturn().getResponse().getStatus();
                };
                results.add(pool.submit(call));
            }
            start.countDown();

            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get(30, TimeUnit.SECONDS));
            }
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("Concurrent setup calls with the same email should yield one 201, one 400 and one OWNER row")
    void setupOwner_concurrentRequests() throws Exception {
        SignupOwnerRequest first = new SignupOwnerRequest(
            "e1234567@u.nus.edu",
            "owner_one",
            "ValidPassword123!",
            CORRECT_SETUP_TOKEN
        );
        SignupOwnerRequest second = new SignupOwnerRequest(
            "e1234567@u.nus.edu",
            "owner_two",
            "ValidPassword123!",
            CORRECT_SETUP_TOKEN
        );

        assertThat(sendConcurrently(first, second)).containsExactlyInAnyOrder(202, 409);
    }

    @Test
    @DisplayName("Concurrent setup calls with different emails should both succeed and create two OWNER rows")
    void setupOwner_concurrentRequestsWithDifferentEmails() throws Exception {
        SignupOwnerRequest first = new SignupOwnerRequest(
            "e1234567@u.nus.edu",
            "owner_one",
            "ValidPassword123!",
            CORRECT_SETUP_TOKEN
        );
        SignupOwnerRequest second = new SignupOwnerRequest(
            "e2234567@u.nus.edu",
            "owner_two",
            "ValidPassword123!",
            CORRECT_SETUP_TOKEN
        );

        assertThat(sendConcurrently(first, second)).containsExactly(202, 202);
    }

    @Test
    @DisplayName("Health endpoint should be reachable without credentials")
    void healthEndpoint_isPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk());
    }
}
