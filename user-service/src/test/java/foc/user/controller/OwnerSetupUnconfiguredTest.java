/*
AI Assistance Disclosure:
Tool: Claude Code (Opus 5.5), date: 2026-11-09
Scope: PR #309 review (@Sinnez1): the unconfigured-token 503 cases for
       /auth/signup-owner and POST /users/me/owner, moved here from
       OwnerSignupControllerTest and OwnerUpgradeControllerTest. This class
       blanks owner.setup.token through its own @TestPropertySource, so it
       runs in its own Spring context and the other classes no longer
       change the shared OwnerSetupToken bean by reflection. The upgrade
       case also checks that the 503 comes before the gate code is spent.
Author review: Leong Wei Zhi to review via the PR.
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
import static org.mockito.Mockito.verify;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import foc.user.PostgresTestContainer;
import foc.user.entity.Role;
import foc.user.entity.User;
import foc.user.repository.AccountUpdateOtpRepository;
import foc.user.repository.PendingSignupRepository;
import foc.user.repository.UserRepository;
import foc.user.security.TokenDenylist;
import foc.user.service.OtpEmailSender;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

// overrides PostgresTestContainer's pinned token, so this class gets its
// own context. Closed after the class, so its pool does not count against
// the shared Postgres's connection limit for the rest of the run
@SpringBootTest
@DirtiesContext
@AutoConfigureMockMvc
@TestPropertySource(properties = "owner.setup.token=")
class OwnerSetupUnconfiguredTest extends PostgresTestContainer {

    private static final String EMAIL = "e1234567@u.nus.edu";
    private static final String ANY_TOKEN = "test-owner-setup-token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountUpdateOtpRepository gateOtps;

    @Autowired
    private PendingSignupRepository pendingSignupRepository;

    // captures the plain codes instead of speaking SMTP
    @MockitoBean
    private OtpEmailSender otpEmailSender;

    // the test stack runs no Redis
    @MockitoBean
    private TokenDenylist tokenDenylist;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @BeforeEach
    void cleanDatabase() {
        gateOtps.deleteAll();
        pendingSignupRepository.deleteAll();
        userRepository.deleteAll();
    }

    // other test classes share the database
    @AfterEach
    void clearRows() {
        gateOtps.deleteAll();
        pendingSignupRepository.deleteAll();
        userRepository.deleteAll();
    }

    private ResultActions postJson(String path, Map<String, String> body) throws Exception {
        return mockMvc.perform(post(path)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)));
    }

    @Test
    @DisplayName("No server-side owner key: /auth/signup-owner answers 503")
    void signupOwner_unconfigured() throws Exception {
        postJson("/auth/signup-owner", Map.of("email", EMAIL, "username", "student_alex",
                "password", "ValidPassword123", "ownerKey", ANY_TOKEN))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.type").value("urn:foc:user:owner-setup-token-missing"))
            .andExpect(jsonPath("$.detail").value("Owner setup is unavailable"));
    }

    @Test
    @DisplayName("No server-side owner key: POST /users/me/owner answers 503 and keeps the gate code")
    void upgrade_unconfiguredKeepsCode() throws Exception {
        User alex = userRepository.save(new User(EMAIL, "student_alex", "hashed_password", Role.USER));
        mockMvc.perform(post("/users/me/otp").with(user(alex.getId().toString())))
            .andExpect(status().isAccepted());
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(otpEmailSender).sendAccountUpdateCode(eq(EMAIL), code.capture(), any());

        mockMvc.perform(post("/users/me/owner").with(user(alex.getId().toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    Map.of("ownerKey", ANY_TOKEN, "otp", code.getValue()))))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.type").value("urn:foc:user:owner-setup-token-missing"))
            .andExpect(jsonPath("$.detail").value("Owner setup is unavailable"));
        assertThat(userRepository.findById(alex.getId()).orElseThrow().getRole()).isEqualTo(Role.USER);
        assertThat(gateOtps.findByUserId(alex.getId())).isPresent();
    }
}
