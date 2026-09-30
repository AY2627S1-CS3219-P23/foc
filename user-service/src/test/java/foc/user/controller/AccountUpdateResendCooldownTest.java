/*
AI Assistance Disclosure:
Tool: Claude Code (Fable 5), date: 2026-09-30
Scope: issue #92's resend cooldown end to end at its real default (60s),
       which AccountUpdateControllerTest cannot cover — the shared test
       yaml turns the cooldown off so that class can resend back to
       back. Its own class because the property override needs its own
       application context (SignupResendCooldownTest's pattern). Covers
       the gate-code resend, the pending-change resend, and the rule
       that replacing a pending change with a different address is
       still a send and still waits.
Author review: Leong Wei Zhi to review via the PR.
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import foc.user.PostgresTestContainer;
import foc.user.entity.Role;
import foc.user.entity.User;
import foc.user.repository.AccountUpdateOtpRepository;
import foc.user.repository.PendingEmailChangeRepository;
import foc.user.repository.UserRepository;
import foc.user.service.OtpEmailSender;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = "user.otp.resend-cooldown=60s")
@AutoConfigureMockMvc
class AccountUpdateResendCooldownTest extends PostgresTestContainer {

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

    @MockitoBean
    private OtpEmailSender otpEmailSender;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private User alex;

    @BeforeEach
    void cleanDatabase() {
        gateOtps.deleteAll();
        pendingEmailChanges.deleteAll();
        userRepository.deleteAll();
        alex = userRepository.save(new User(EMAIL, "student_alex",
            new BCryptPasswordEncoder().encode(PASSWORD), Role.USER));
    }

    private ResultActions requestOtp() throws Exception {
        return mockMvc.perform(post("/users/me/otp").with(user(alex.getId().toString())));
    }

    private ResultActions patchAccount(Map<String, String> body) throws Exception {
        return mockMvc.perform(patch("/users/me").with(user(alex.getId().toString()))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)));
    }

    private String gateCode() {
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(otpEmailSender, atLeastOnce()).sendAccountUpdateCode(eq(EMAIL), code.capture(), any());
        return code.getValue();
    }

    @Test
    @DisplayName("A gate-code resend inside the cooldown is 429 with Retry-After; the live code survives")
    void gateResendInsideCooldownRefused() throws Exception {
        requestOtp().andExpect(status().isAccepted());
        String code = gateCode();

        requestOtp()
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
            .andExpect(jsonPath("$.type").value("urn:foc:user:otp-resend-cooldown"));

        // the refusal sent nothing and changed nothing: the code already
        // in the inbox still gates a change
        assertThat(gateCode()).isEqualTo(code);
        patchAccount(Map.of("username", "renamed_alex", "otp", code))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("A pending-change resend inside the cooldown is refused, same for a replacement address")
    void emailChangeSendsInsideCooldownRefused() throws Exception {
        requestOtp().andExpect(status().isAccepted());
        patchAccount(Map.of("email", NEW_EMAIL, "otp", gateCode()))
            .andExpect(status().isAccepted());

        mockMvc.perform(post("/users/me/email/resend").with(user(alex.getId().toString())))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
            .andExpect(jsonPath("$.type").value("urn:foc:user:otp-resend-cooldown"));

        // consuming the gate deleted its row, so a new gate code may be
        // requested at once — but the pending change's own cooldown still
        // refuses a send to a different address: replacing is not a way
        // around the rate limit
        requestOtp().andExpect(status().isAccepted());
        patchAccount(Map.of("email", "e1111111@u.nus.edu", "otp", gateCode()))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists(HttpHeaders.RETRY_AFTER));

        // refused before anything was replaced: the original change survives
        assertThat(pendingEmailChanges.findByUserId(alex.getId()).orElseThrow().getNewEmail())
            .isEqualTo(NEW_EMAIL);
    }
}
