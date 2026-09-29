/*
AI Assistance Disclosure:
Tool: Claude Code (Opus 5.5), date: 2026-09-29
Scope: integration tests for the JWT filter chain (issue #91) against the
       shared Testcontainers Postgres: bearer tokens from login reach the
       /users routes with the role from the token, missing and bad tokens
       get problem+json 401s with the reason, the wrong role gets a
       problem+json 403, /auth/** and /actuator/health ignore a stale token
       (author's decision) but /auth/logout does not, and no session is
       created.
Author review: Ryan to review via the PR.
*/

package foc.user.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import foc.user.PostgresTestContainer;
import foc.user.entity.Role;
import foc.user.entity.User;
import foc.user.repository.UserRepository;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
class JwtAuthenticationTest extends PostgresTestContainer {

    // matches src/test/resources/application.yaml
    private static final String JWT_SECRET = "test-jwt-secret-that-is-at-least-32-bytes-long";
    private static final String PASSWORD = "ValidPassword123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtIssuer jwtIssuer;

    @Autowired
    private JwtVerifier jwtVerifier;

    @Autowired
    private SecurityProblemResponses problems;

    private User alex;
    private User admin;
    private User owner;

    @BeforeEach
    void seedUsers() {
        userRepository.deleteAll();
        alex = userRepository.save(new User("e1234567@u.nus.edu", "student_alex",
            passwordEncoder.encode(PASSWORD), Role.USER));
        admin = userRepository.save(new User("admin@u.nus.edu", "an_admin", "hashed_password", Role.ADMIN));
        owner = userRepository.save(new User("owner@u.nus.edu", "the_owner", "hashed_password", Role.OWNER));
    }

    private static RequestPostProcessor bearer(String token) {
        return request -> {
            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            return request;
        };
    }

    private String expiredToken(User user) {
        Instant twoHoursAgo = Instant.now().minus(Duration.ofHours(2));
        return new JwtIssuer(JWT_SECRET, Duration.ofHours(1), Clock.fixed(twoHoursAgo, ZoneOffset.UTC))
            .issue(user);
    }

    // ---- valid tokens ----

    @Test
    @DisplayName("A token from POST /auth/login reaches GET /users/me as that user")
    void loginTokenReachesOwnProfile() throws Exception {
        String body = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonMapper.builder().build().writeValueAsString(
                    Map.of("usernameOrEmail", "student_alex", "password", PASSWORD))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        String token = JsonMapper.builder().build().readTree(body).get("accessToken").asString();

        mockMvc.perform(get("/users/me").with(bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(alex.getId()))
            .andExpect(jsonPath("$.username").value("student_alex"));
    }

    @Test
    @DisplayName("The scheme name is case-insensitive")
    void lowerCaseScheme() throws Exception {
        mockMvc.perform(get("/users/me")
                .header(HttpHeaders.AUTHORIZATION, "bearer " + jwtIssuer.issue(alex)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(alex.getId()));
    }

    @Test
    @DisplayName("ADMIN and OWNER tokens reach the admin routes")
    void adminRolesReachAdminRoutes() throws Exception {
        mockMvc.perform(get("/users").with(bearer(jwtIssuer.issue(admin))))
            .andExpect(status().isOk());
        mockMvc.perform(get("/users").with(bearer(jwtIssuer.issue(owner))))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("A USER token can use the routes open to any signed-in user")
    void userTokenReachesProfileRoutes() throws Exception {
        String token = jwtIssuer.issue(alex);

        mockMvc.perform(get("/users/{id}", admin.getId()).with(bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("an_admin"));
        mockMvc.perform(delete("/users/me").with(bearer(token)))
            .andExpect(status().isNoContent());

        assertThat(userRepository.findById(alex.getId()).orElseThrow().isActive()).isFalse();
    }

    @Test
    @DisplayName("The caller's id comes from the token's sub")
    void callerFromSubject() throws Exception {
        mockMvc.perform(delete("/users/{id}", alex.getId()).with(bearer(jwtIssuer.issue(admin))))
            .andExpect(status().isNoContent());
        // removing yourself through the admin route is blocked, which needs
        // the caller id to be the admin's own
        mockMvc.perform(delete("/users/{id}", admin.getId()).with(bearer(jwtIssuer.issue(admin))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.detail").value("Use DELETE /users/me to delete your own account"));
    }

    @Test
    @DisplayName("The role comes from the token, not the database")
    void roleFromToken() throws Exception {
        // design doc §4: services read role from the claim, no lookup
        String userToken = jwtIssuer.issue(alex);
        alex.setRole(Role.ADMIN);
        userRepository.save(alex);

        mockMvc.perform(get("/users").with(bearer(userToken)))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Requests don't create a session")
    void stateless() throws Exception {
        MvcResult result = mockMvc.perform(get("/users/me").with(bearer(jwtIssuer.issue(alex))))
            .andExpect(status().isOk())
            .andReturn();

        assertThat(result.getRequest().getSession(false)).isNull();
    }

    // ---- 401 / 403 ----

    @Test
    @DisplayName("No token on a protected route is a problem+json 401")
    void missingToken() throws Exception {
        mockMvc.perform(get("/users/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.type").value("about:blank"))
            .andExpect(jsonPath("$.title").value("Unauthorized"))
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.detail").value("Authentication required"))
            .andExpect(jsonPath("$.instance").value("/users/me"));
    }

    @Test
    @DisplayName("Another auth scheme counts as no token")
    void otherScheme() throws Exception {
        mockMvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.detail").value("Authentication required"));
    }

    @Test
    @DisplayName("An expired token is a problem+json 401 saying so")
    void expiredToken() throws Exception {
        mockMvc.perform(get("/users/me").with(bearer(expiredToken(alex))))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\""))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.detail").value("Token has expired"))
            .andExpect(jsonPath("$.instance").value("/users/me"));
    }

    @Test
    @DisplayName("A malformed, wrongly signed or empty token is a problem+json 401")
    void invalidToken() throws Exception {
        String wrongSecret = new JwtIssuer("another-secret-that-is-also-at-least-32-bytes", Duration.ofHours(1))
            .issue(alex);

        for (String token : new String[] {"not.a.jwt", wrongSecret, ""}) {
            mockMvc.perform(get("/users/me").with(bearer(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\""))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Invalid token"));
        }
    }

    @Test
    @DisplayName("A bad token on an admin route is 401, not 403")
    void invalidTokenOnAdminRoute() throws Exception {
        mockMvc.perform(get("/users").with(bearer("not.a.jwt")))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.detail").value("Invalid token"));
    }

    @Test
    @DisplayName("A USER token on an admin route is a problem+json 403")
    void wrongRole() throws Exception {
        mockMvc.perform(get("/users").with(bearer(jwtIssuer.issue(alex))))
            .andExpect(status().isForbidden())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.type").value("about:blank"))
            .andExpect(jsonPath("$.title").value("Forbidden"))
            .andExpect(jsonPath("$.status").value(403))
            .andExpect(jsonPath("$.detail").value("You do not have permission to access this resource"))
            .andExpect(jsonPath("$.instance").value("/users"));
    }

    @Test
    @DisplayName("401s and 403s carry the CORS header so the SPA can read them")
    void errorsReadableCrossOrigin() throws Exception {
        String webOrigin = "http://localhost:5173";
        mockMvc.perform(get("/users/me").header(HttpHeaders.ORIGIN, webOrigin).with(bearer("not.a.jwt")))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, webOrigin));
        mockMvc.perform(get("/users").header(HttpHeaders.ORIGIN, webOrigin).with(bearer(jwtIssuer.issue(alex))))
            .andExpect(status().isForbidden())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, webOrigin));
    }

    // ---- routes the filter skips ----

    @Test
    @DisplayName("A stale token doesn't block POST /auth/login")
    void staleTokenOnLogin() throws Exception {
        mockMvc.perform(post("/auth/login")
                .with(bearer(expiredToken(alex)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonMapper.builder().build().writeValueAsString(
                    Map.of("usernameOrEmail", "student_alex", "password", PASSWORD))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    @DisplayName("A garbage token doesn't block POST /auth/signup")
    void garbageTokenOnSignup() throws Exception {
        mockMvc.perform(post("/auth/signup")
                .with(bearer("not.a.jwt"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonMapper.builder().build().writeValueAsString(
                    Map.of("email", "e7654321@u.nus.edu", "username", "new_student", "password", PASSWORD))))
            .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("A garbage token doesn't block the health check")
    void garbageTokenOnHealth() throws Exception {
        mockMvc.perform(get("/actuator/health").with(bearer("not.a.jwt")))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("The filter skips /auth/** and /actuator/health, but not /auth/logout")
    void skippedPaths() {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtVerifier, problems);

        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("POST", "/auth/login"))).isTrue();
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("POST", "/auth/signup"))).isTrue();
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("POST", "/auth/setup-owner"))).isTrue();
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/actuator/health"))).isTrue();

        // logout reads the caller's token (#90)
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("POST", "/auth/logout"))).isFalse();
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/users/me"))).isFalse();
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/users"))).isFalse();
        // a prefix match only on whole path segments
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/authx"))).isFalse();
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/actuator/healthx"))).isFalse();
    }
}
