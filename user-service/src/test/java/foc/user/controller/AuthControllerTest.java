/*
AI Assistance Disclosure:
Tool: Claude Code (Opus 5.5), date: 2026-09-29
Scope: integration tests for POST /auth/signup and POST /auth/login
       (issues #87/#89/#90) against the shared Testcontainers Postgres:
       created account, exact 400 reasons, non-revealing 401s, lockout,
       token claims, and the CORS preflight for the web origin.
       PR #141 review: usernames match ignoring case at sign-up and login
       (team decision).
Author review: Ryan to review via the PR.
*/

package foc.user.controller;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
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
import foc.user.entity.Role;
import foc.user.entity.User;
import foc.user.repository.UserRepository;
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
    private static final String LOGIN_FAILED = "Incorrect username/email or password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @BeforeEach
    void cleanDatabase() {
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

    private ResultActions login(String usernameOrEmail, String password) throws Exception {
        return postJson("/auth/login", Map.of("usernameOrEmail", usernameOrEmail, "password", password));
    }

    // ---- sign-up ----

    @Test
    @DisplayName("Sign-up creates a USER account and returns it without the password")
    void signup_created() throws Exception {
        signup("e1234567@u.nus.edu", "student_alex", PASSWORD)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.email").value("e1234567@u.nus.edu"))
            .andExpect(jsonPath("$.username").value("student_alex"))
            .andExpect(jsonPath("$.role").value("USER"))
            .andExpect(jsonPath("$.password").doesNotExist())
            .andExpect(jsonPath("$.passwordHash").doesNotExist());

        User saved = userRepository.findByUsernameIgnoreCase("student_alex").orElseThrow();
        assertThat(saved.getPasswordHash()).isNotEqualTo(PASSWORD).startsWith("$2");
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
        signup("e1234567@u.nus.edu", "student_alex", PASSWORD).andExpect(status().isCreated());

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
    @DisplayName("Sign-up ignores a role in the body: the account is always USER")
    void signup_roleIgnored() throws Exception {
        postJson("/auth/signup", Map.of(
                "email", "e1234567@u.nus.edu",
                "username", "student_alex",
                "password", PASSWORD,
                "role", "ADMIN"))
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
        signup("e1234567@u.nus.edu", "student_alex", PASSWORD).andExpect(status().isCreated());
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
        signup("e1234567@u.nus.edu", "Student_Alex", PASSWORD).andExpect(status().isCreated());

        login("student_alex", PASSWORD).andExpect(status().isOk());
        login("STUDENT_ALEX", PASSWORD).andExpect(status().isOk());
        assertThat(userRepository.findByUsernameIgnoreCase("student_alex").orElseThrow().getUsername())
            .isEqualTo("Student_Alex");
    }

    @Test
    @DisplayName("Unknown account and wrong password get the same 401 problem+json")
    void login_nonRevealing() throws Exception {
        signup("e1234567@u.nus.edu", "student_alex", PASSWORD).andExpect(status().isCreated());

        for (String identifier : new String[] { "student_alex", "nobody" }) {
            login(identifier, "WrongPassword123")
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(LOGIN_FAILED));
        }
    }

    @Test
    @DisplayName("After 5 failures the account is locked, even for the right password")
    void login_lockout() throws Exception {
        signup("e1234567@u.nus.edu", "student_alex", PASSWORD).andExpect(status().isCreated());

        for (int i = 0; i < 5; i++) {
            login("student_alex", "WrongPassword123").andExpect(status().isUnauthorized());
        }

        login("student_alex", PASSWORD)
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.detail").value(LOGIN_FAILED));
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
