/*
AI Assistance Disclosure:
Tool: Claude Code (Opus 5.5), date: 2026-10-28
Scope: issue #267, revised USR-8 (team decision): every path that inserts
       a user row (sign-up, owner setup, the demo seeder) leaves exactly
       one user.created for that user in the outbox, through the real
       endpoints and the real seeder against Postgres.
Author review: Ryan Ang, pending pull request review.
*/

package foc.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;

import foc.user.PostgresTestContainer;
import foc.user.entity.OutboxEvent;
import foc.user.entity.User;
import foc.user.repository.OutboxEventRepository;
import foc.user.repository.PendingSignupRepository;
import foc.user.repository.UserRepository;
import foc.user.seed.DemoAccountsSeeder;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
class UserCreatedEventTest extends PostgresTestContainer {

    private static final String PASSWORD = "ValidPassword123";
    private static final String OWNER_KEY = "user-created-event-test-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PendingSignupRepository pendingSignupRepository;

    @Autowired
    private OutboxEventRepository outboxEvents;

    @Autowired
    private OwnerSignupService ownerSignupService;

    @Autowired
    private UserCreation userCreation;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private OtpEmailSender otpEmailSender;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @BeforeEach
    void cleanDatabase() {
        pendingSignupRepository.deleteAll();
        userRepository.deleteAll();
        outboxEvents.deleteAll();
        // the setup key on the shared bean, as OwnerSignupControllerTest sets it
        ReflectionTestUtils.setField(ownerSignupService, "expectedOwnerKey", OWNER_KEY);
    }

    // other test classes share the database
    @AfterEach
    void cleanUp() {
        userRepository.deleteAll();
        outboxEvents.deleteAll();
    }

    @Test
    void aVerifiedSignUpWritesOneUserCreated() throws Exception {
        post("/auth/signup", Map.of("email", "e1111111@u.nus.edu", "username", "event_signup",
            "password", PASSWORD));
        verifyWithEmailedCode("/auth/signup/verify", "e1111111@u.nus.edu");

        assertExactlyOneUserCreatedFor("event_signup");
    }

    @Test
    void aVerifiedOwnerSetupWritesOneUserCreated() throws Exception {
        post("/auth/signup-owner", Map.of("email", "e2222222@u.nus.edu", "username", "event_owner",
            "password", PASSWORD, "ownerKey", OWNER_KEY));
        verifyWithEmailedCode("/auth/signup/verify-owner", "e2222222@u.nus.edu");

        assertExactlyOneUserCreatedFor("event_owner");
    }

    @Test
    void theSeederWritesOneUserCreatedPerAccount() {
        new DemoAccountsSeeder(userRepository, userCreation, passwordEncoder, transactionManager).run();

        List<User> seeded = userRepository.findAll();
        assertThat(seeded).hasSize(1 + DemoAccountsSeeder.ADMINS + DemoAccountsSeeder.USERS);
        assertThat(outboxEvents.count()).isEqualTo(seeded.size());
        for (User user : seeded) {
            assertExactlyOneUserCreatedFor(user.getUsername());
        }
    }

    private void post(String path, Map<String, String> body) throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(body)))
            .andExpect(status().isAccepted());
    }

    private void verifyWithEmailedCode(String path, String email) throws Exception {
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(otpEmailSender, atLeastOnce()).sendSignupCode(eq(email), code.capture(), any(), anyBoolean());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(Map.of("email", email, "code", code.getValue()))))
            .andExpect(status().isCreated());
    }

    private void assertExactlyOneUserCreatedFor(String username) {
        String userId = String.valueOf(userRepository.findByUsernameIgnoreCase(username).orElseThrow().getId());
        List<OutboxEvent> events = outboxEvents.findAll().stream()
            .filter(event -> jsonMapper.readTree(event.getPayload()).path("userId").stringValue().equals(userId))
            .toList();
        assertThat(events).as("user.created for " + username).singleElement().satisfies(event -> {
            assertThat(event.getEventType()).isEqualTo("user.created");
            assertThat(jsonMapper.readTree(event.getPayload()).path("correlationId").stringValue())
                .isEqualTo(userId);
        });
    }
}
