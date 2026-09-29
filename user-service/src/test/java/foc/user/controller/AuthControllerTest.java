/*
AI Assistance Disclosure:
Tool: Claude Code (Opus 5.5), date: 2026-09-29
Scope: integration tests for POST /auth/signup and POST /auth/login
       (issues #87/#89/#90) against the shared Testcontainers Postgres:
       created account, exact 400 reasons, non-revealing 401s, lockout,
       token claims, and the CORS preflight for the web origin.
       PR #141 review: usernames match ignoring case at sign-up and login
       (team decision).
       2026-09-29, Claude Code (Opus 5), issue #146: the 401s are no longer
       non-revealing — they name the cause — so the tests pin one detail per
       cause, the countdown as it runs down, and the 429's Retry-After.
       2026-09-29, Claude Code (Fable 5), issue #88: sign-up now answers
       202 and the account only exists after /auth/signup/verify, so the
       sign-up cases assert the pending shape, the flow tests drive verify
       with the code captured off the mocked OtpEmailSender (mocked over
       GreenMail on purpose: no new dependency, and the SMTP layer has its
       own unit test + the manual Mailpit check), and the login cases
       create their accounts through the full flow.
Author review: Leong Wei Zhi to review via the PR.
*/

package foc.user.controller;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import foc.user.PostgresTestContainer;
import foc.user.entity.PendingSignup;
import foc.user.entity.Role;
import foc.user.entity.User;
import foc.user.repository.PendingSignupRepository;
import foc.user.repository.UserRepository;
import foc.user.service.OtpEmailSender;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest extends PostgresTestContainer {

    // matches src/test/resources/application.yaml
    private static final String JWT_SECRET = "test-jwt-secret-that-is-at-least-32-bytes-long";
    private static final String WEB_ORIGIN = "http://localhost:5173";
    private static final String PASSWORD = "ValidPassword123";
    private static final String UNKNOWN_ACCOUNT = "No account found for that username or email.";
    private static final String LOCKED = "Your account is locked due to too many failed login"
        + " attempts. Try again in 15 minutes.";

    // the countdown the server quotes after the n-th consecutive failure
    private static String wrongPassword(int remaining) {
        return "Incorrect password. " + (remaining == 1 ? "1 attempt" : remaining + " attempts")
            + " remaining before your account is temporarily locked.";
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PendingSignupRepository pendingSignupRepository;

    // captures the plain code instead of speaking SMTP; reset per test
    @MockitoBean
    private OtpEmailSender otpEmailSender;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @BeforeEach
    void cleanDatabase() {
        pendingSignupRepository.deleteAll();
        userRepository.deleteAll();
    }

    private ResultActions postJson(String path, Map<String, String> body) throws Exception {
        return mockMvc.perform(post(path)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)));
    }

    private ResultActions signup(String email, String username, String password) throws Exception {
        return postJson("/auth/signup", Map.of("email", email, "username", username, "password", password));
    }

    private ResultActions verifySignup(String email, String code) throws Exception {
        return postJson("/auth/signup/verify", Map.of("email", email, "code", code));
    }

    private ResultActions login(String usernameOrEmail, String password) throws Exception {
        return postJson("/auth/login", Map.of("usernameOrEmail", usernameOrEmail, "password", password));
    }

    // the last code the mocked sender was handed for this (normalised) email
    private String emailedCode(String email) {
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(otpEmailSender, atLeastOnce()).sendSignupCode(eq(email), code.capture(), any());
        return code.getValue();
    }

    // full sign-up flow: request, code off the mocked sender, verify
    private void createAccount(String email, String username) throws Exception {
        signup(email, username, PASSWORD).andExpect(status().isAccepted());
        verifySignup(email, emailedCode(email)).andExpect(status().isCreated());
    }

    // ---- sign-up ----

    @Test
    @DisplayName("Sign-up answers 202 with a pending row and an emailed code — no account yet")
    void signup_accepted() throws Exception {
        signup("e1234567@u.nus.edu", "student_alex", PASSWORD)
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.email").value("e1234567@u.nus.edu"))
            .andExpect(jsonPath("$.expiresInSeconds").value(600));

        assertThat(userRepository.findByUsernameIgnoreCase("student_alex")).isEmpty();
        PendingSignup pending = pendingSignupRepository.findByEmail("e1234567@u.nus.edu").orElseThrow();
        String code = emailedCode("e1234567@u.nus.edu");
        // both secrets are stored hashed, never plain
        assertThat(pending.getPasswordHash()).isNotEqualTo(PASSWORD).startsWith("$2");
        assertThat(pending.getCodeHash()).isNotEqualTo(code).startsWith("$2");
    }

    @Test
    @DisplayName("Verifying the emailed code creates the account, which can then log in")
    void signup_verifyFlow() throws Exception {
        signup("e1234567@u.nus.edu", "student_alex", PASSWORD).andExpect(status().isAccepted());

        verifySignup("e1234567@u.nus.edu", emailedCode("e1234567@u.nus.edu"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.email").value("e1234567@u.nus.edu"))
            .andExpect(jsonPath("$.username").value("student_alex"))
            .andExpect(jsonPath("$.role").value("USER"))
            .andExpect(jsonPath("$.password").doesNotExist())
            .andExpect(jsonPath("$.passwordHash").doesNotExist());

        User saved = userRepository.findByUsernameIgnoreCase("student_alex").orElseThrow();
        assertThat(saved.getPasswordHash()).isNotEqualTo(PASSWORD).startsWith("$2");
        assertThat(pendingSignupRepository.findByEmail("e1234567@u.nus.edu")).isEmpty();

        login("student_alex", PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("A wrong code is 400; the right one still works after it")
    void signup_wrongCodeThenRight() throws Exception {
        signup("e1234567@u.nus.edu", "student_alex", PASSWORD).andExpect(status().isAccepted());
        String code = emailedCode("e1234567@u.nus.edu");
        String wrong = code.equals("000000") ? "000001" : "000000";

        verifySignup("e1234567@u.nus.edu", wrong)
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.detail").value("Invalid verification code"));

        verifySignup("e1234567@u.nus.edu", code).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("The fifth wrong code discards the pending sign-up with a 429; re-signing up recovers")
    void signup_attemptsExhausted() throws Exception {
        signup("e1234567@u.nus.edu", "student_alex", PASSWORD).andExpect(status().isAccepted());
        String code = emailedCode("e1234567@u.nus.edu");
        String wrong = code.equals("000000") ? "000001" : "000000";

        for (int attempt = 1; attempt <= 4; attempt++) {
            verifySignup("e1234567@u.nus.edu", wrong).andExpect(status().isBadRequest());
        }
        verifySignup("e1234567@u.nus.edu", wrong)
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.detail").value(
                "Too many incorrect codes; sign up again to get a new code"));

        // the row is gone, so even the right code is just a wrong code now
        verifySignup("e1234567@u.nus.edu", code)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Invalid verification code"));

        // a fresh sign-up issues a fresh code and completes
        createAccount("e1234567@u.nus.edu", "student_alex");
    }

    @Test
    @DisplayName("A repeat sign-up resends: the new code wins, the old one no longer counts")
    void signup_repeatResends() throws Exception {
        signup("e1234567@u.nus.edu", "student_alex", PASSWORD).andExpect(status().isAccepted());
        String first = emailedCode("e1234567@u.nus.edu");

        signup("e1234567@u.nus.edu", "student_alex", PASSWORD).andExpect(status().isAccepted());
        String second = emailedCode("e1234567@u.nus.edu");

        if (!second.equals(first)) {
            verifySignup("e1234567@u.nus.edu", first)
                .andExpect(status().isBadRequest());
        }
        verifySignup("e1234567@u.nus.edu", second).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Verify with no pending sign-up answers like a wrong code")
    void signup_verifyWithoutSignup() throws Exception {
        verifySignup("e1234567@u.nus.edu", "123456")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Invalid verification code"));
    }

    @Test
    @DisplayName("Sign-up with a non-NUS email is 400 with the exact reason")
    void signup_invalidEmail() throws Exception {
        signup("alex@gmail.com", "student_alex", PASSWORD)
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.detail").value(
                "Email must be a valid @u.nus.edu address. Email used should not be the friendly email."));
    }

    @Test
    @DisplayName("Sign-up with a weak password is 400 with the exact reason")
    void signup_weakPassword() throws Exception {
        signup("e1234567@u.nus.edu", "student_alex", "alllowercase1")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value(
                "Password must contain at least one uppercase letter, one lowercase letter, and one number."));
    }

    @Test
    @DisplayName("Sign-up with a taken email or username is 400 with the exact reason")
    void signup_duplicates() throws Exception {
        createAccount("e1234567@u.nus.edu", "student_alex");

        signup("E1234567@u.nus.edu", "someone_else", PASSWORD)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Email is already registered"));
        signup("e7654321@u.nus.edu", "student_alex", PASSWORD)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Username is already taken"));
        // usernames are unique ignoring case
        signup("e7654321@u.nus.edu", "Student_Alex", PASSWORD)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Username is already taken"));
    }

    @Test
    @DisplayName("An identifier taken while a sign-up was pending fails its verify")
    void signup_identifierTakenWhilePending() throws Exception {
        signup("e1234567@u.nus.edu", "student_alex", PASSWORD).andExpect(status().isAccepted());
        String code = emailedCode("e1234567@u.nus.edu");
        // someone else completes the same username first
        createAccount("e7654321@u.nus.edu", "student_alex");

        verifySignup("e1234567@u.nus.edu", code)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Username is already taken"));
    }

    @Test
    @DisplayName("Sign-up ignores a role in the body: the account is always USER")
    void signup_roleIgnored() throws Exception {
        postJson("/auth/signup", Map.of(
                "email", "e1234567@u.nus.edu",
                "username", "student_alex",
                "password", PASSWORD,
                "role", "ADMIN"))
            .andExpect(status().isAccepted());

        verifySignup("e1234567@u.nus.edu", emailedCode("e1234567@u.nus.edu"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.role").value("USER"));

        assertThat(userRepository.findByUsernameIgnoreCase("student_alex").orElseThrow().getRole()).isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("Several broken rules are all reported, one sentence each")
    void signup_severalReasons() throws Exception {
        signup("", "", PASSWORD)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value(allOf(
                containsString("Email is required."),
                containsString("Username is required."))));
    }

    @Test
    @DisplayName("Sign-up with no body is 400")
    void signup_noBody() throws Exception {
        mockMvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Request body is missing or malformed"));
    }

    // ---- login ----

    @Test
    @DisplayName("Login by username or email returns a token for the account")
    void login_success() throws Exception {
        createAccount("e1234567@u.nus.edu", "student_alex");
        Long id = userRepository.findByUsernameIgnoreCase("student_alex").orElseThrow().getId();

        for (String identifier : new String[] { "student_alex", "E1234567@u.nus.edu" }) {
            String body = login(identifier, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andReturn().getResponse().getContentAsString();

            String token = objectMapper.readTree(body).get("accessToken").asString();
            Claims claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(token)
                .getPayload();
            assertThat(claims.getSubject()).isEqualTo(String.valueOf(id));
            assertThat(claims.get("role", String.class)).isEqualTo("USER");
        }
    }

    @Test
    @DisplayName("Login matches the username ignoring case; the account keeps its own case")
    void login_usernameIgnoresCase() throws Exception {
        createAccount("e1234567@u.nus.edu", "Student_Alex");

        login("student_alex", PASSWORD).andExpect(status().isOk());
        login("STUDENT_ALEX", PASSWORD).andExpect(status().isOk());
        assertThat(userRepository.findByUsernameIgnoreCase("student_alex").orElseThrow().getUsername())
            .isEqualTo("Student_Alex");
    }

    @Test
    @DisplayName("An unknown account and a wrong password get different 401 problem+json details")
    void login_namesTheFailure() throws Exception {
        createAccount("e1234567@u.nus.edu", "student_alex");

        login("nobody", "WrongPassword123")
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.detail").value(UNKNOWN_ACCOUNT));

        login("student_alex", "WrongPassword123")
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.detail").value(wrongPassword(4)));
    }

    @Test
    @DisplayName("After 5 failures the account is locked, even for the right password")
    void login_lockout() throws Exception {
        createAccount("e1234567@u.nus.edu", "student_alex");

        // the countdown runs down to the singular on the last attempt
        for (int remaining = 4; remaining > 0; remaining--) {
            login("student_alex", "WrongPassword123")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value(wrongPassword(remaining)));
        }
        // the fifth failure trips the lock and already says so (issue #145:
        // the lockout is deliberately distinguishable, as a 429)
        login("student_alex", "WrongPassword123")
            .andExpect(status().isTooManyRequests())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(header().string(HttpHeaders.RETRY_AFTER, "900"))
            .andExpect(jsonPath("$.detail").value(LOCKED));

        login("student_alex", PASSWORD)
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
            .andExpect(jsonPath("$.detail").value(LOCKED));
        assertThat(userRepository.findByUsernameIgnoreCase("student_alex").orElseThrow().getLockedUntil()).isNotNull();
    }

    @Test
    @DisplayName("Login with a blank field is 400")
    void login_blankField() throws Exception {
        login("", PASSWORD)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Username or email is required."));
    }

    // ---- CORS ----

    @Test
    @DisplayName("A preflight from the web origin is allowed")
    void cors_webOriginAllowed() throws Exception {
        mockMvc.perform(options("/auth/login")
                .header(HttpHeaders.ORIGIN, WEB_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, WEB_ORIGIN));
    }

    @Test
    @DisplayName("A preflight to an admin route is answered before the role rules")
    void cors_adminRoutePreflight() throws Exception {
        mockMvc.perform(options("/users/5")
                .header(HttpHeaders.ORIGIN, WEB_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PATCH")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization, Content-Type"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, WEB_ORIGIN));
    }

    @Test
    @DisplayName("A preflight from any other origin is rejected")
    void cors_otherOriginRejected() throws Exception {
        mockMvc.perform(options("/auth/login")
                .header(HttpHeaders.ORIGIN, "https://evil.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("The OWNER role in the token is taken from the account")
    void login_ownerRoleClaim() throws Exception {
        User owner = new User("e1111111@u.nus.edu", "root_owner",
            new BCryptPasswordEncoder().encode(PASSWORD), Role.OWNER);
        userRepository.save(owner);

        String body = login("root_owner", PASSWORD)
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(body).get("accessToken").asString();
        String role = Jwts.parser()
            .verifyWith(Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8)))
            .build()
            .parseSignedClaims(token)
            .getPayload()
            .get("role", String.class);
        assertThat(role).isEqualTo("OWNER");
    }
}
