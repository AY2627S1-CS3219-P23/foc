/*
AI Assistance Disclosure:
Tool: Claude Code (Opus 5), date: 2026-09-29
Scope: PR #150 author review: the resend cooldown end to end at its real
       default (60s), which AuthControllerTest cannot cover — that class
       resends back to back, so the shared test yaml turns the cooldown
       off. Its own class rather than a nested one because the override
       needs its own application context.
Author review: Leong Wei Zhi to review via the PR.
2026-09-30 (Claude Code, Fable 5), issue #92: the 429 also pins its new
problem+json type URI.
*/

package foc.user.controller;

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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import foc.user.PostgresTestContainer;
import foc.user.repository.PendingSignupRepository;
import foc.user.repository.UserRepository;
import foc.user.service.OtpEmailSender;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = "user.otp.resend-cooldown=60s")
@AutoConfigureMockMvc
class SignupResendCooldownTest extends PostgresTestContainer {

    private static final String EMAIL = "e1234567@u.nus.edu";
    private static final String PASSWORD = "ValidPassword123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PendingSignupRepository pendingSignupRepository;

    @MockitoBean
    private OtpEmailSender otpEmailSender;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @BeforeEach
    void cleanDatabase() {
        pendingSignupRepository.deleteAll();
        userRepository.deleteAll();
    }

    private ResultActions signup(String username, String password) throws Exception {
        return mockMvc.perform(post("/auth/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(
                Map.of("email", EMAIL, "username", username, "password", password))));
    }

    private String emailedCode() {
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(otpEmailSender, atLeastOnce()).sendSignupCode(eq(EMAIL), code.capture(), any());
        return code.getValue();
    }

    @Test
    @DisplayName("A resend inside the cooldown is 429 with Retry-After, and the live code survives")
    void resendInsideCooldownRefused() throws Exception {
        signup("student_alex", PASSWORD).andExpect(status().isAccepted());
        String code = emailedCode();

        signup("student_alex", PASSWORD)
            .andExpect(status().isTooManyRequests())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
            .andExpect(jsonPath("$.type").value("urn:foc:user:otp-resend-cooldown"))
            .andExpect(jsonPath("$.detail").value(
                org.hamcrest.Matchers.startsWith("A verification code was sent to this email")));

        // the refusal sent nothing and changed nothing: the code already
        // in the inbox is still the one that verifies
        assertThat(emailedCode()).isEqualTo(code);
        mockMvc.perform(post("/auth/signup/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", EMAIL, "code", code))))
            .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("The cooldown doesn't shadow the 409: other details are refused as a conflict")
    void otherDetailsStillConflict() throws Exception {
        signup("student_alex", PASSWORD).andExpect(status().isAccepted());

        // only someone repeating the pending request's own details can be
        // told to wait; anyone else is refused outright, so the 429 never
        // becomes a way to probe for a pending sign-up with a wrong password
        signup("attacker_x", "AttackerPass123").andExpect(status().isConflict());
    }
}
