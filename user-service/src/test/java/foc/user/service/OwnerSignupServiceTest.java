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
*/

package foc.user.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

import foc.user.controller.ProblemTypes;
import foc.user.dto.SignupOwnerRequest;
import foc.user.dto.SignupResponse;
import foc.user.dto.SignupVerifyRequest;
import foc.user.dto.UserResponse;
import foc.user.entity.PendingSignup;
import foc.user.entity.Role;
import foc.user.entity.User;
import foc.user.exception.OtpVerificationException;
import foc.user.repository.PendingSignupRepository;
import foc.user.repository.UserRepository;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class OwnerSignupServiceTest {

    // values needed to build service
    private static final String VALID_SETUP_TOKEN = "correct-setup-token";
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

    //@Mock
    //private PasswordEncoder passwordEncoder;

    @InjectMocks
    private OwnerSignupService ownerSignupService;

    @BeforeEach
    void setUpDefaultStubs() {
        //lenient().when(userRepository.existsByEmail(anyString())).thenReturn(false);
        //lenient().when(userRepository.existsByUsernameIgnoreCase(anyString())).thenReturn(false);
        //lenient().when(passwordEncoder.encode(anyString())).thenReturn("hashed_password");

        otpService =
            new OtpService(PLAIN_ENCODER, OTP_TTL, OTP_MAX_ATTEMPTS, OTP_RESEND_COOLDOWN);

        ownerSignupService = new OwnerSignupService(userRepository, pendingSignupRepository, PLAIN_ENCODER,
            otpService, otpEmailSender, Clock.fixed(NOW, ZoneOffset.UTC), VALID_SETUP_TOKEN);

        reset(pendingSignupRepository);
        reset(userRepository);
    }

    
    // --- happy path ---

    @Test
    @DisplayName("Signup and verify create user with owner")
    void setupOwner_savesOwnerAndReturnsResponse() {
        when(userRepository.saveAndFlush(any(User.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        SignupOwnerRequest signRequest = new SignupOwnerRequest(
            "e1234567@u.nus.edu", "owner_user", PASSWORD, VALID_SETUP_TOKEN
        );
        SignupResponse signResponse = ownerSignupService.signupOwner(signRequest);

        ArgumentCaptor<PendingSignup> saved = ArgumentCaptor.forClass(PendingSignup.class);
        verify(pendingSignupRepository).saveAndFlush(saved.capture());
        verify(pendingSignupRepository, never()).findByEmail(any());
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(otpEmailSender).sendSignupCode(eq("e1234567@u.nus.edu"), code.capture(), eq(OTP_TTL));

        PendingSignup existing = new PendingSignup("e1234567@u.nus.edu", "owner_user",
            PLAIN_ENCODER.encode(PASSWORD), PLAIN_ENCODER.encode(code.capture()), NOW, NOW.plus(OTP_TTL));
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
    @DisplayName("Missing owner key should pass signup and fail verify")
    void setupOwner_throwsForbiddenWhenTokenMissing() {
        SignupOwnerRequest signRequest = new SignupOwnerRequest(
            "e1234567@u.nus.edu", "owner_user", PASSWORD, null);
        SignupResponse signResponse = ownerSignupService.signupOwner(signRequest);

        verify(pendingSignupRepository, never()).saveAndFlush(any());
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(otpEmailSender).sendSignupCode(eq("e1234567@u.nus.edu"), code.capture(), eq(OTP_TTL));

        SignupVerifyRequest verifyRequest = new SignupVerifyRequest("e1234567@u.nus.edu", code.capture());
        assertThatThrownBy(() ->  ownerSignupService.verifySignup(verifyRequest))
            .isInstanceOf(OtpVerificationException.class)
            .satisfies(ex ->
                assertThat(((OtpVerificationException) ex).type())
                    .isEqualTo(ProblemTypes.OTP_INVALID)
            );
    }

    @Test
    @DisplayName("Wrong owner key should pass signup and fail verify")
    void setupOwner_throwsForbiddenWhenTokenWrong() {
        SignupOwnerRequest signRequest = new SignupOwnerRequest(
            "e1234567@u.nus.edu", "owner_user", PASSWORD, "wrongKey");
        SignupResponse signResponse = ownerSignupService.signupOwner(signRequest);

        verify(pendingSignupRepository, never()).saveAndFlush(any());
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(otpEmailSender).sendSignupCode(eq("e1234567@u.nus.edu"), code.capture(), eq(OTP_TTL));

        SignupVerifyRequest verifyRequest = new SignupVerifyRequest("e1234567@u.nus.edu", code.capture());
        assertThatThrownBy(() ->  ownerSignupService.verifySignup(verifyRequest))
            .isInstanceOf(OtpVerificationException.class)
            .satisfies(ex ->
                assertThat(((OtpVerificationException) ex).type())
                    .isEqualTo(ProblemTypes.OTP_INVALID)
            );
    }

    @Test
    @DisplayName("Should throw 503 Service Unavailable when OWNER_SETUP_TOKEN is not configured")
    void setupOwner_throwsServiceUnavailableWhenTokenNotConfigured() {
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
                    .isEqualTo("Owner setup is disabled: OWNER_SETUP_TOKEN is not configured")
            );
    }

    // checks uniqueness of details

    @Test
    @DisplayName("Should throw 400 when email is already registered")
    void setupOwner_throwsBadRequestOnDuplicateEmail() {
        when(userRepository.existsByEmail("e1234567@u.nus.edu")).thenReturn(true);

        SignupOwnerRequest request = new SignupOwnerRequest(
            "e1234567@u.nus.edu", "owner_user", "ValidPassword123!", VALID_SETUP_TOKEN
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
    void setupOwner_throwsBadRequestOnDuplicateUsername() {
        when(userRepository.existsByUsernameIgnoreCase("owner_user")).thenReturn(true);

        SignupOwnerRequest request = new SignupOwnerRequest(
            "e1234567@u.nus.edu", "owner_user", PASSWORD, VALID_SETUP_TOKEN
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
    void setupOwner_normalisesEmailToLowercase() {
        when(userRepository.saveAndFlush(any(User.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        SignupOwnerRequest signRequest = new SignupOwnerRequest(
            " E1234567@u.nus.edu ", "owner_user", PASSWORD, VALID_SETUP_TOKEN
        );
        SignupResponse signResponse = ownerSignupService.signupOwner(signRequest);
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);

        PendingSignup existing = new PendingSignup("e1234567@u.nus.edu", "owner_user",
            PLAIN_ENCODER.encode(PASSWORD), PLAIN_ENCODER.encode(code.capture()), NOW, NOW.plus(OTP_TTL));
        when(pendingSignupRepository.findWithLockByEmail("e1234567@u.nus.edu"))
            .thenReturn(Optional.of(existing));
        
        SignupVerifyRequest verifyRequest = new SignupVerifyRequest("e1234567@u.nus.edu", code.capture());
        UserResponse verifyResponse = ownerSignupService.verifySignup(verifyRequest);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());

        assertThat(captor.getValue().getEmail()).isEqualTo("e1234567@u.nus.edu");
    }

    @Test
    @DisplayName("Should trim leading and trailing whitespace from username before saving")
    void setupOwner_trimsUsernameWhitespace() {
                when(userRepository.saveAndFlush(any(User.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        SignupOwnerRequest signRequest = new SignupOwnerRequest(
            "e1234567@u.nus.edu", "  owner_user  ", PASSWORD, VALID_SETUP_TOKEN
        );
        SignupResponse signResponse = ownerSignupService.signupOwner(signRequest);
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);

        PendingSignup existing = new PendingSignup("e1234567@u.nus.edu", "owner_user",
            PLAIN_ENCODER.encode(PASSWORD), PLAIN_ENCODER.encode(code.capture()), NOW, NOW.plus(OTP_TTL));
        when(pendingSignupRepository.findWithLockByEmail("e1234567@u.nus.edu"))
            .thenReturn(Optional.of(existing));
        
        SignupVerifyRequest verifyRequest = new SignupVerifyRequest("e1234567@u.nus.edu", code.capture());
        UserResponse verifyResponse = ownerSignupService.verifySignup(verifyRequest);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("owner_user");
    }

}
