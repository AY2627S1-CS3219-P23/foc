/*
AI Assistance Disclosure:
Tool: Claude (Sonnet 4.6), date: 2026-09-22
Scope: Generated test code to test controller layer. Generated code included set up of testcontainers running Postgres.
       Additional cases added for blank fields, password character class violations, and invalid username characters.
Author review: Ryan validated correctness and naming.
2026-09-23 (Claude Code, Opus 5.5): file renamed from OwnerSetUpControllerTest.java;
       moved to Jackson 3 / Testcontainers 2.x; added cases for mixed-case and
       padded email, the password length message, concurrent setup requests,
       and an unauthenticated /actuator/health (PR #126 review).

*/


package foc.user.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import foc.user.dto.SetupOwnerRequest;
import foc.user.repository.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class OwnerSetupControllerTest {

    private static final String VALID_SETUP_TOKEN = "test-owner-setup-token";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @BeforeEach
    void cleanDatabase() {
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Should successfully bootstrap initial OWNER when 0 owners exist")
    void setupFirstOwner_success() throws Exception {
        SetupOwnerRequest request = new SetupOwnerRequest(
            "e1234567@u.nus.edu",
            "root_owner",
            "ValidPassword123!"
        );

        mockMvc.perform(post("/auth/setup-owner")
                .header("X-Setup-Token", VALID_SETUP_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.email").value("e1234567@u.nus.edu"))
            .andExpect(jsonPath("$.username").value("root_owner"))
            .andExpect(jsonPath("$.role").value("OWNER"))
            .andExpect(jsonPath("$.password").doesNotExist())
            .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("Should reject second setup call with 409 Conflict")
    void setupFirstOwner_secondCallFailsWith409() throws Exception {
        SetupOwnerRequest first = new SetupOwnerRequest(
            "e1234567@u.nus.edu",
            "owner_one",
            "ValidPassword123!"
        );
        mockMvc.perform(post("/auth/setup-owner")
                .header("X-Setup-Token", VALID_SETUP_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(first)))
            .andExpect(status().isCreated());

        // Attempting to bootstrap a second time must fail with 409
        SetupOwnerRequest second = new SetupOwnerRequest(
            "e2234567@u.nus.edu",
            "owner_two",
            "ValidPassword123!"
        );
        mockMvc.perform(post("/auth/setup-owner")
                .header("X-Setup-Token", VALID_SETUP_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(second)))
            .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Should reject non-NUS email domain with 400 Bad Request")
    void setupFirstOwner_invalidEmailDomain() throws Exception {
        SetupOwnerRequest invalidEmailRequest = new SetupOwnerRequest(
            "e1234567@gmail.com",
            "owner_user",
            "ValidPassword123!"
        );

        mockMvc.perform(post("/auth/setup-owner")
                .header("X-Setup-Token", VALID_SETUP_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidEmailRequest)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should reject short password (< 10 chars) with 400 Bad Request")
    void setupFirstOwner_shortPassword() throws Exception {
        SetupOwnerRequest shortPasswordRequest = new SetupOwnerRequest(
            "e1234567@u.nus.edu",
            "owner_user",
            "short" // Only 5 chars
        );

        mockMvc.perform(post("/auth/setup-owner")
                .header("X-Setup-Token", VALID_SETUP_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(shortPasswordRequest)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should reject blank email with 400 Bad Request")
    void setupFirstOwner_blankEmail() throws Exception {
        SetupOwnerRequest request = new SetupOwnerRequest(
            "",
            "owner_user",
            "ValidPassword123!"
        );

        mockMvc.perform(post("/auth/setup-owner")
                .header("X-Setup-Token", VALID_SETUP_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should reject password with no uppercase letter with 400 Bad Request")
    void setupFirstOwner_passwordMissingUppercase() throws Exception {
        SetupOwnerRequest request = new SetupOwnerRequest(
            "e1234567@u.nus.edu",
            "owner_user",
            "validpassword123!" // no uppercase
        );

        mockMvc.perform(post("/auth/setup-owner")
                .header("X-Setup-Token", VALID_SETUP_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should reject password with no digit with 400 Bad Request")
    void setupFirstOwner_passwordMissingDigit() throws Exception {
        SetupOwnerRequest request = new SetupOwnerRequest(
            "e1234567@u.nus.edu",
            "owner_user",
            "ValidPasswordOnly!" // no digit
        );

        mockMvc.perform(post("/auth/setup-owner")
                .header("X-Setup-Token", VALID_SETUP_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should reject username with special characters with 400 Bad Request")
    void setupFirstOwner_invalidUsernameCharacters() throws Exception {
        SetupOwnerRequest request = new SetupOwnerRequest(
            "e1234567@u.nus.edu",
            "owner-user!", // hyphens and ! not allowed
            "ValidPassword123!"
        );

        mockMvc.perform(post("/auth/setup-owner")
                .header("X-Setup-Token", VALID_SETUP_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should reject setup call with no setup token with 403 Forbidden")
    void setupFirstOwner_missingSetupToken() throws Exception {
        SetupOwnerRequest request = new SetupOwnerRequest(
            "e1234567@u.nus.edu",
            "owner_user",
            "ValidPassword123!"
        );

        mockMvc.perform(post("/auth/setup-owner")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should reject setup call with wrong setup token with 403 Forbidden")
    void setupFirstOwner_wrongSetupToken() throws Exception {
        SetupOwnerRequest request = new SetupOwnerRequest(
            "e1234567@u.nus.edu",
            "owner_user",
            "ValidPassword123!"
        );

        mockMvc.perform(post("/auth/setup-owner")
                .header("X-Setup-Token", "wrong-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should accept mixed-case, padded NUS email and store it normalised")
    void setupFirstOwner_mixedCasePaddedEmail() throws Exception {
        SetupOwnerRequest request = new SetupOwnerRequest(
            " E1234567@U.NUS.EDU ",
            "owner_user",
            "ValidPassword123!"
        );

        mockMvc.perform(post("/auth/setup-owner")
                .header("X-Setup-Token", VALID_SETUP_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.email").value("e1234567@u.nus.edu"));
    }

    @Test
    @DisplayName("Should reject over-long password (> 50 chars) with 400 Bad Request")
    void setupFirstOwner_longPassword() throws Exception {
        SetupOwnerRequest request = new SetupOwnerRequest(
            "e1234567@u.nus.edu",
            "owner_user",
            "Aa1" + "x".repeat(60)
        );

        mockMvc.perform(post("/auth/setup-owner")
                .header("X-Setup-Token", VALID_SETUP_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());

        assertThat(userRepository.countByRole("OWNER")).isZero();
    }

    @Test
    @DisplayName("Concurrent setup calls should yield exactly one 201, one 409 and one OWNER row")
    void setupFirstOwner_concurrentRequests() throws Exception {
        SetupOwnerRequest first = new SetupOwnerRequest(
            "e1234567@u.nus.edu",
            "owner_one",
            "ValidPassword123!"
        );
        SetupOwnerRequest second = new SetupOwnerRequest(
            "e2234567@u.nus.edu",
            "owner_two",
            "ValidPassword123!"
        );

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (SetupOwnerRequest request : List.of(first, second)) {
                Callable<Integer> call = () -> {
                    start.await();
                    return mockMvc.perform(post("/auth/setup-owner")
                            .header("X-Setup-Token", VALID_SETUP_TOKEN)
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

            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
            assertThat(userRepository.countByRole("OWNER")).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("Health endpoint should be reachable without credentials")
    void healthEndpoint_isPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk());
    }
}
