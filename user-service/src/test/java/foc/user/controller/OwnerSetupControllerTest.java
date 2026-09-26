/*
AI Assistance Disclosure:
Tool: Claude (Sonnet 4.6), date: 2026-09-22
Scope: Generated test code to test controller layer. Generated code included set up of testcontainers running Postgres.
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
*/


package foc.user.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import foc.user.PostgresTestContainer;
import foc.user.dto.SetupOwnerRequest;
import foc.user.entity.Role;
import foc.user.repository.UserRepository;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
class OwnerSetupControllerTest extends PostgresTestContainer {

    private static final String VALID_SETUP_TOKEN = "test-owner-setup-token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @BeforeEach
    void cleanDatabase() {
        userRepository.deleteAll();
    }

    private long ownerCount() {
        return userRepository.findAll().stream()
            .filter(user -> user.getRole() == Role.OWNER)
            .count();
    }

    @Test
    @DisplayName("Should successfully create an OWNER with a valid setup token")
    void setupOwner_success() throws Exception {
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
    @DisplayName("Should allow a second setup call to create another OWNER")
    void setupOwner_secondCallCreatesAnotherOwner() throws Exception {
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

        SetupOwnerRequest second = new SetupOwnerRequest(
            "e2234567@u.nus.edu",
            "owner_two",
            "ValidPassword123!"
        );
        mockMvc.perform(post("/auth/setup-owner")
                .header("X-Setup-Token", VALID_SETUP_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(second)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.role").value("OWNER"));

        assertThat(ownerCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should reject non-NUS email domain with 400 Bad Request")
    void setupOwner_invalidEmailDomain() throws Exception {
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
    void setupOwner_shortPassword() throws Exception {
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
    void setupOwner_blankEmail() throws Exception {
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
    void setupOwner_passwordMissingUppercase() throws Exception {
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
    void setupOwner_passwordMissingDigit() throws Exception {
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
    void setupOwner_invalidUsernameCharacters() throws Exception {
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
    void setupOwner_missingSetupToken() throws Exception {
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
    void setupOwner_wrongSetupToken() throws Exception {
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
    void setupOwner_mixedCasePaddedEmail() throws Exception {
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
    void setupOwner_longPassword() throws Exception {
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

        assertThat(ownerCount()).isZero();
    }

    // sends both requests at the same moment and returns their status codes
    private List<Integer> sendConcurrently(SetupOwnerRequest first, SetupOwnerRequest second) throws Exception {
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
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("Concurrent setup calls with the same email should yield one 201, one 400 and one OWNER row")
    void setupOwner_concurrentRequests() throws Exception {
        SetupOwnerRequest first = new SetupOwnerRequest(
            "e1234567@u.nus.edu",
            "owner_one",
            "ValidPassword123!"
        );
        SetupOwnerRequest second = new SetupOwnerRequest(
            "e1234567@u.nus.edu",
            "owner_two",
            "ValidPassword123!"
        );

        assertThat(sendConcurrently(first, second)).containsExactlyInAnyOrder(201, 400);
        assertThat(ownerCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Concurrent setup calls with different emails should both succeed and create two OWNER rows")
    void setupOwner_concurrentRequestsWithDifferentEmails() throws Exception {
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

        assertThat(sendConcurrently(first, second)).containsExactly(201, 201);
        assertThat(ownerCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Health endpoint should be reachable without credentials")
    void healthEndpoint_isPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk());
    }
}
