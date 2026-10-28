/*
AI Assistance Disclosure:
2026-09-22 (Claude, Sonnet 4.6): Generated unit tests for OwnerSetupService covering owner guard,
       duplicate checks, email/username normalisation, and the happy path.
       Tests use Mockito to isolate the service from the database.
Author review: Ryan validated test assertions to match intended behaviour.
2026-09-23 (Claude, Sonnet 4.6): added setup-token guard tests (missing token,
       wrong token, token not configured).
2026-09-23 (Claude Code, Fable 5), issue #86: role literals switched to the
       Role enum following the entity's String-to-enum conversion.
2026-09-25 (Claude Code, Opus 5.5): existing-owner guard test replaced by one
       asserting setup never checks for existing owners. Test names
       follow the setupFirstOwner -> signupOwner rename.
2026-09-26 (Claude Code, Opus 5.5), issue #96: UserRepository.countByRole
       removed; the existing-owners test now checks setup makes only the
       uniqueness, lock and save calls.
2026-09-29 (Claude Code, Opus 5.5), PR #141 review: username checks use
       existsByUsernameIgnoreCase (usernames now ignore case, team decision).
2026-10-02 (Claude Code, Opus 5.5), issue #154: the 503's reason is checked
       to be the generic "Owner setup is unavailable".
2026-10-10 (Claude Code, Opus 5.5), PR #162 re-review (Leong Wei Zhi): the
       unconfigured-token log line is checked to be written once.
2026-10-19: Parts copied from AuthServiceTest, which was AI-assisted
2026-10-28 (Claude Code, Opus 5.5), issue #267, revised USR-8: built with a
real UserCreation over the mocked repository and a mocked EventOutbox, and
a verified owner setup writes user.created. Author review: Ryan Ang,
pending pull request review.
*/

package foc.user.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import foc.user.dto.SignupOwnerRequest;
import foc.user.dto.SignupVerifyRequest;
import foc.user.dto.UserResponse;
import foc.user.entity.PendingSignup;
import foc.user.entity.Role;
import foc.user.entity.User;
import foc.user.repository.PendingSignupRepository;
import foc.user.repository.UserRepository;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class OwnerSignupServiceTest {

    // values needed to build service
    private static final String VALID_SETUP_KEY = "correct-setup-key";
    private static final Instant NOW = Instant.parse("2026-09-29T10:00:00Z");
    private static final String PASSWORD = "ValidPassword123";
    private static final Duration OTP_TTL = Duration.ofMinutes(10);
    private static final int OTP_MAX_ATTEMPTS = 5;
    private static final Duration OTP_RESEND_COOLDOWN = Duration.ofSeconds(60);
    private static final PasswordEncoder PLAIN_ENCODER = new PasswordEncoder() {
        @Override
        public String encode(CharSequence raw) {
            return "hashed:" + raw;
        }

        @Override
        public boolean matches(CharSequence raw, String encoded) {
            return encode(raw).equals(encoded);
        }
    };

    @Mock
    private PendingSignupRepository pendingSignupRepository;

    @Mock
    private OtpEmailSender otpEmailSender;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OtpService otpService;

    @Mock
    private EventOutbox eventOutbox;

    //@Mock
    //private PasswordEncoder passwordEncoder;

    @InjectMocks
    private OwnerSignupService ownerSignupService;

    // what saveAndFlush answers: the user, now with the id the insert gave it
    private static User insertedWithId(org.mockito.invocation.InvocationOnMock inv) {
        User inserted = inv.getArgument(0);
        ReflectionTestUtils.setField(inserted, "id", 7L);
        return inserted;
    }

    @BeforeEach
    void setUpDefaultStubs() {
        //lenient().when(userRepository.existsByEmail(anyString())).thenReturn(false);
        //lenient().when(userRepository.existsByUsernameIgnoreCase(anyString())).thenReturn(false);
        //lenient().when(passwordEncoder.encode(anyString())).thenReturn("hashed_password");

        otpService =
            new OtpService(PLAIN_ENCODER, OTP_TTL, OTP_MAX_ATTEMPTS, OTP_RESEND_COOLDOWN);

        ownerSignupService = new OwnerSignupService(userRepository, pendingSignupRepository, PLAIN_ENCODER,
            otpService, otpEmailSender, Clock.fixed(NOW, ZoneOffset.UTC), VALID_SETUP_KEY,
            new UserCreation(userRepository, eventOutbox));

        reset(pendingSignupRepository);
        reset(userRepository);
    }

    
    // --- happy path ---

    @Test
    @DisplayName("Signup and verify creates owner")
    void signupOwner_savesOwnerAndReturnsResponse() {
        when(userRepository.saveAndFlush(any(User.class)))
            .thenAnswer(OwnerSignupServiceTest::insertedWithId);

        SignupOwnerRequest signRequest = new SignupOwnerRequest(
            "e1234567@u.nus.edu", "owner_user", PASSWORD, VALID_SETUP_KEY
        );
        ownerSignupService.signupOwner(signRequest);

        ArgumentCaptor<PendingSignup> saved = ArgumentCaptor.forClass(PendingSignup.class);
        verify(pendingSignupRepository).saveAndFlush(saved.capture());
        verify(pendingSignupRepository, never()).findByEmail(any());
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(otpEmailSender).sendSignupCode(eq("e1234567@u.nus.edu"), code.capture(), eq(OTP_TTL), eq(true));

        PendingSignup existing = new PendingSignup("e1234567@u.nus.edu", "owner_user",
            PLAIN_ENCODER.encode(PASSWORD), PLAIN_ENCODER.encode(code.capture()), NOW, NOW.plus(OTP_TTL), true);
        when(pendingSignupRepository.findWithLockByEmail("e1234567@u.nus.edu"))
            .thenReturn(Optional.of(existing));
        SignupVerifyRequest verifyRequest = new SignupVerifyRequest("e1234567@u.nus.edu", code.capture());
        UserResponse verifyResponse = ownerSignupService.verifySignup(verifyRequest);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());

        assertThat(captor.getValue().getRole()).isEqualTo(Role.OWNER);
        assertThat(captor.getValue().getPasswordHash()).isEqualTo(PLAIN_ENCODER.encode(PASSWORD));
        assertThat(verifyResponse.email()).isEqualTo("e1234567@u.nus.edu");        // credit-service opens the owner's account from this (revised USR-8)
        verify(eventOutbox).userCreated(7L);
    }

    @Test
    @DisplayName("Missing owner key should create user, notified via otp email")
    void signupOwner_KeyMissing() {
        when(userRepository.saveAndFlush(any(User.class)))
            .thenAnswer(OwnerSignupServiceTest::insertedWithId);

        SignupOwnerRequest signRequest = new SignupOwnerRequest(
            "e1234567@u.nus.edu", "owner_user", PASSWORD, null);
        ownerSignupService.signupOwner(signRequest);

        ArgumentCaptor<PendingSignup> saved = ArgumentCaptor.forClass(PendingSignup.class);
        verify(pendingSignupRepository).saveAndFlush(saved.capture());
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(otpEmailSender).sendSignupCode(eq("e1234567@u.nus.edu"), code.capture(), eq(OTP_TTL), eq(false));

        when(pendingSignupRepository.findWithLockByEmail("e1234567@u.nus.edu"))
            .thenReturn(Optional.of(saved.getValue()));
        SignupVerifyRequest verifyRequest = new SignupVerifyRequest("e1234567@u.nus.edu", code.getValue());
        UserResponse verifyResponse = ownerSignupService.verifySignup(verifyRequest);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        
        assertThat(captor.getValue().getRole()).isEqualTo(Role.USER);
        assertThat(captor.getValue().getPasswordHash()).isEqualTo(PLAIN_ENCODER.encode(PASSWORD));
        assertThat(verifyResponse.email()).isEqualTo("e1234567@u.nus.edu");
    }

    @Test
    @DisplayName("Wrong owner key should create user, notified via otp email")
    void signupOwner_WrongKey() {
        when(userRepository.saveAndFlush(any(User.class)))
            .thenAnswer(OwnerSignupServiceTest::insertedWithId);

        SignupOwnerRequest signRequest = new SignupOwnerRequest(
            "e1234567@u.nus.edu", "owner_user", PASSWORD, "wrongkey");
        ownerSignupService.signupOwner(signRequest);

        ArgumentCaptor<PendingSignup> saved = ArgumentCaptor.forClass(PendingSignup.class);
        verify(pendingSignupRepository).saveAndFlush(saved.capture());
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(otpEmailSender).sendSignupCode(eq("e1234567@u.nus.edu"), code.capture(), eq(OTP_TTL), eq(false));

        when(pendingSignupRepository.findWithLockByEmail("e1234567@u.nus.edu"))
            .thenReturn(Optional.of(saved.getValue()));
        SignupVerifyRequest verifyRequest = new SignupVerifyRequest("e1234567@u.nus.edu", code.getValue());
        UserResponse verifyResponse = ownerSignupService.verifySignup(verifyRequest);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        
        assertThat(captor.getValue().getRole()).isEqualTo(Role.USER);
        assertThat(captor.getValue().getPasswordHash()).isEqualTo(PLAIN_ENCODER.encode(PASSWORD));
        assertThat(verifyResponse.email()).isEqualTo("e1234567@u.nus.edu");
    }

    @Test
    @DisplayName("Refreshing expired request should read ownerKey again")
    void signupOwner_refreshReadsNewKey() {
        PendingSignup expired = new PendingSignup("e1234567@u.nus.edu", "owner_user",
            PLAIN_ENCODER.encode(PASSWORD), PLAIN_ENCODER.encode("old code"), NOW.minus(OTP_TTL), NOW.minus(OTP_TTL), false);
        when(pendingSignupRepository.findWithLockByEmail("e1234567@u.nus.edu"))
            .thenReturn(Optional.of(expired));
        when(userRepository.saveAndFlush(any(User.class)))
            .thenAnswer(OwnerSignupServiceTest::insertedWithId);

        SignupOwnerRequest signRequest = new SignupOwnerRequest(
            "e1234567@u.nus.edu", "owner_user", PASSWORD, VALID_SETUP_KEY
        );
        ownerSignupService.signupOwner(signRequest);

        ArgumentCaptor<PendingSignup> saved = ArgumentCaptor.forClass(PendingSignup.class);
        verify(pendingSignupRepository).saveAndFlush(saved.capture());
        verify(pendingSignupRepository, never()).findByEmail(any());
        assertTrue(saved.getValue().getIsOwner());
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(otpEmailSender).sendSignupCode(eq("e1234567@u.nus.edu"), code.capture(), eq(OTP_TTL), eq(true));

        PendingSignup existing = new PendingSignup("e1234567@u.nus.edu", "owner_user",
            PLAIN_ENCODER.encode(PASSWORD), PLAIN_ENCODER.encode(code.capture()), NOW, NOW.plus(OTP_TTL), true);
        when(pendingSignupRepository.findWithLockByEmail("e1234567@u.nus.edu"))
            .thenReturn(Optional.of(existing));
        SignupVerifyRequest verifyRequest = new SignupVerifyRequest("e1234567@u.nus.edu", code.capture());
        UserResponse verifyResponse = ownerSignupService.verifySignup(verifyRequest);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());

        assertThat(captor.getValue().getRole()).isEqualTo(Role.OWNER);
        assertThat(captor.getValue().getPasswordHash()).isEqualTo(PLAIN_ENCODER.encode(PASSWORD));
        assertThat(verifyResponse.email()).isEqualTo("e1234567@u.nus.edu");
    }


    @Test
    @DisplayName("Should throw 503 when OWNER_SETUP_KEY is not configured")
    void signupOwner_throwsServiceUnavailableWhenKeyNotConfigured() {
        ReflectionTestUtils.setField(ownerSignupService, "expectedOwnerKey", "");

        SignupOwnerRequest signRequest = new SignupOwnerRequest(
            "e1234567@u.nus.edu", "owner_user", PASSWORD, "wrongKey");

        assertThatThrownBy(() -> ownerSignupService.signupOwner(signRequest))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex ->
                assertThat(((ResponseStatusException) ex).getStatusCode())
                    .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
            )
            // the reason reaches the caller, so it must not name the setting
            .satisfies(ex ->
                assertThat(((ResponseStatusException) ex).getReason())
                    .isEqualTo("Owner setup is unavailable")
            );
    }

    // checks uniqueness of details

    @Test
    @DisplayName("Should throw 400 when email is already registered")
    void signupOwner_throwsBadRequestOnDuplicateEmail() {
        when(userRepository.existsByEmail("e1234567@u.nus.edu")).thenReturn(true);

        SignupOwnerRequest request = new SignupOwnerRequest(
            "e1234567@u.nus.edu", "owner_user", "ValidPassword123!", VALID_SETUP_KEY
        );

        assertThatThrownBy(() -> ownerSignupService.signupOwner(request))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex ->
                assertThat(((ResponseStatusException) ex).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST)
            );
    }

    @Test
    @DisplayName("Should throw 400 when username is already taken")
    void signupOwner_throwsBadRequestOnDuplicateUsername() {
        when(userRepository.existsByUsernameIgnoreCase("owner_user")).thenReturn(true);

        SignupOwnerRequest request = new SignupOwnerRequest(
            "e1234567@u.nus.edu", "owner_user", PASSWORD, VALID_SETUP_KEY
        );

        assertThatThrownBy(() -> ownerSignupService.signupOwner(request))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex ->
                assertThat(((ResponseStatusException) ex).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST)
            );
    }

    // normalise email and usernames

    @Test
    @DisplayName("Should normalise email to lowercase before saving")
    void signupOwner_normalisesEmailToLowercase() {
        when(userRepository.saveAndFlush(any(User.class)))
            .thenAnswer(OwnerSignupServiceTest::insertedWithId);

        SignupOwnerRequest signRequest = new SignupOwnerRequest(
            " E1234567@u.nus.edu ", "owner_user", PASSWORD, VALID_SETUP_KEY
        );
        ownerSignupService.signupOwner(signRequest);
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);

        PendingSignup existing = new PendingSignup("e1234567@u.nus.edu", "owner_user",
            PLAIN_ENCODER.encode(PASSWORD), PLAIN_ENCODER.encode(code.capture()), NOW, NOW.plus(OTP_TTL), true);
        when(pendingSignupRepository.findWithLockByEmail("e1234567@u.nus.edu"))
            .thenReturn(Optional.of(existing));
        
        SignupVerifyRequest verifyRequest = new SignupVerifyRequest("e1234567@u.nus.edu", code.capture());
        ownerSignupService.verifySignup(verifyRequest);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());

        assertThat(captor.getValue().getEmail()).isEqualTo("e1234567@u.nus.edu");
    }

    @Test
    @DisplayName("Should trim leading and trailing whitespace from username before saving")
    void signupOwner_trimsUsernameWhitespace() {
                when(userRepository.saveAndFlush(any(User.class)))
            .thenAnswer(OwnerSignupServiceTest::insertedWithId);

        SignupOwnerRequest signRequest = new SignupOwnerRequest(
            "e1234567@u.nus.edu", "  owner_user  ", PASSWORD, VALID_SETUP_KEY
        );
        ownerSignupService.signupOwner(signRequest);
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);

        PendingSignup existing = new PendingSignup("e1234567@u.nus.edu", "owner_user",
            PLAIN_ENCODER.encode(PASSWORD), PLAIN_ENCODER.encode(code.capture()), NOW, NOW.plus(OTP_TTL), true);
        when(pendingSignupRepository.findWithLockByEmail("e1234567@u.nus.edu"))
            .thenReturn(Optional.of(existing));
        
        SignupVerifyRequest verifyRequest = new SignupVerifyRequest("e1234567@u.nus.edu", code.capture());
        ownerSignupService.verifySignup(verifyRequest);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("owner_user");
    }

}
