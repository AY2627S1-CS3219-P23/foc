/*
AI Assistance Disclosure:
Tool: Claude Code (Fable 5), date: 2026-10-11
Scope: integration tests for POST /auth/logout (issue #151) against the
       shared Testcontainers Postgres: a valid token answers 204 and hands
       its own jti/exp to the denylist, missing and bad tokens are the
       filter's problem+json 401s. TokenDenylist is mocked like
       AuthControllerTest's OtpEmailSender (no Redis in the test stack —
       the service only writes, and the write itself is unit-tested in
       TokenDenylistTest).
Author review: Leong Wei Zhi to review via the PR.
*/

package foc.user.controller;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import foc.user.PostgresTestContainer;
import foc.user.entity.Role;
import foc.user.entity.User;
import foc.user.repository.UserRepository;
import foc.user.security.TokenDenylist;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
class LogoutTest extends PostgresTestContainer {

    // matches src/test/resources/application-test.yaml
    private static final String JWT_SECRET = "test-jwt-secret-that-is-at-least-32-bytes-long";
    private static final String PASSWORD = "ValidPassword123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    // the Redis writer, mocked like AuthControllerTest's OtpEmailSender:
    // no Redis runs in the test stack, and the write is unit-tested
    @MockitoBean
    private TokenDenylist tokenDenylist;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @BeforeEach
    void cleanDatabase() {
        userRepository.deleteAll();
    }

    private String loginToken() throws Exception {
        userRepository.save(new User("e1234567@u.nus.edu", "student_alex",
            new BCryptPasswordEncoder().encode(PASSWORD), Role.USER));
        String body = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    Map.of("usernameOrEmail", "student_alex", "password", PASSWORD))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asString();
    }

    private Claims claims(String token) {
        return Jwts.parser()
            .verifyWith(Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8)))
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    @Test
    @DisplayName("Logout answers 204 and denylists the caller's own jti until its exp")
    void logout_denylistsOwnToken() throws Exception {
        String token = loginToken();
        Claims claims = claims(token);

        mockMvc.perform(post("/auth/logout")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isNoContent());

        assertThat(claims.getId()).isNotBlank();
        verify(tokenDenylist).revoke(claims.getId(), claims.getExpiration().toInstant());
    }

    @Test
    @DisplayName("Logout without a token is the filter's problem+json 401")
    void logout_withoutToken() throws Exception {
        mockMvc.perform(post("/auth/logout"))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.detail").value("Authentication required"));

        verifyNoInteractions(tokenDenylist);
    }

    @Test
    @DisplayName("Logout with a garbage token is a 401 and denylists nothing")
    void logout_withGarbageToken() throws Exception {
        mockMvc.perform(post("/auth/logout")
                .header(HttpHeaders.AUTHORIZATION, "Bearer not.a.jwt"))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.detail").value("Invalid token"));

        verifyNoInteractions(tokenDenylist);
    }
}
