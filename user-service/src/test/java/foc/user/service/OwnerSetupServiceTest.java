/*
AI Assistance Disclosure:
Tool: Claude (Sonnet 4.6), date: 2026-09-22
Scope: Generated unit tests for OwnerSetupService covering owner guard,
       duplicate checks, email/username normalisation, and the happy path.
       Tests use Mockito to isolate the service from the database.
Author review: Ryan validated test assertions to match intended behaviour.
2026-09-23 (Claude, Sonnet 4.6): added setup-token guard tests (missing token,
       wrong token, token not configured).
2026-09-23 (Claude Code, Fable 5), issue #86: role literals switched to the
       Role enum following the entity's String-to-enum conversion.
2026-09-25 (Claude Code, Opus 5.5): existing-owner guard test replaced by one
       asserting setup never checks for existing owners. Test names
       follow the setupFirstOwner -> setupOwner rename.
2026-09-25 (Claude Code, Opus 5.5): token expiry cases added (expired -> 403,
       expiry unset -> 503, wrong token checked before expiry).
*/

package foc.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import foc.user.dto.SetupOwnerRequest;
import foc.user.dto.UserResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import foc.user.entity.Role;
import foc.user.entity.User;
import foc.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class OwnerSetupServiceTest {

    private static final String VALID_SETUP_TOKEN = "correct-setup-token";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private OwnerSetupService ownerSetupService;

    @BeforeEach
    void setUpDefaultStubs() {
        ReflectionTestUtils.setField(ownerSetupService, "expectedSetupToken", VALID_SETUP_TOKEN);
        ReflectionTestUtils.setField(ownerSetupService, "tokenExpiresAt",
            Instant.now().plus(1, ChronoUnit.HOURS));
        lenient().when(userRepository.existsByEmail(anyString())).thenReturn(false);
        lenient().when(userRepository.existsByUsername(anyString())).thenReturn(false);
        lenient().when(passwordEncoder.encode(anyString())).thenReturn("hashed_password");
    }

    // setup token guard

    @Test
    @DisplayName("Should throw 403 Forbidden when setup token is missing")
    void setupOwner_throwsForbiddenWhenTokenMissing() {
        SetupOwnerRequest request = new SetupOwnerRequest(
            "e1234567@u.nus.edu", "owner_user", "ValidPassword123!"
        );

        assertThatThrownBy(() -> ownerSetupService.setupOwner(request, null))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex ->
                assertThat(((ResponseStatusException) ex).getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN)
            );
    }

    @Test
    @DisplayName("Should throw 403 Forbidden when setup token is wrong")
    void setupOwner_throwsForbiddenWhenTokenWrong() {
        SetupOwnerRequest request = new SetupOwnerRequest(
            "e1234567@u.nus.edu", "owner_user", "ValidPassword123!"
        );

        assertThatThrownBy(() -> ownerSetupService.setupOwner(request, "wrong-token"))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex ->
                assertThat(((ResponseStatusException) ex).getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN)
            );
    }

    @Test
    @DisplayName("Should throw 503 Service Unavailable when OWNER_SETUP_TOKEN is not configured")
    void setupOwner_throwsServiceUnavailableWhenTokenNotConfigured() {
        ReflectionTestUtils.setField(ownerSetupService, "expectedSetupToken", "");

        SetupOwnerRequest request = new SetupOwnerRequest(
            "e1234567@u.nus.edu", "owner_user", "ValidPassword123!"
        );

        assertThatThrownBy(() -> ownerSetupService.setupOwner(request, VALID_SETUP_TOKEN))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex ->
                assertThat(((ResponseStatusException) ex).getStatusCode())
                    .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
            );
    }

    // token expiry

    @Test
    @DisplayName("Should throw 403 Forbidden when the setup token has expired")
    void setupOwner_throwsForbiddenWhenTokenExpired() {
        ReflectionTestUtils.setField(ownerSetupService, "tokenExpiresAt",
            Instant.now().minus(1, ChronoUnit.MINUTES));

        SetupOwnerRequest request = new SetupOwnerRequest(
            "e1234567@u.nus.edu", "owner_user", "ValidPassword123!"
        );

        assertThatThrownBy(() -> ownerSetupService.setupOwner(request, VALID_SETUP_TOKEN))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                ResponseStatusException rse = (ResponseStatusException) ex;
                assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                assertThat(rse.getReason()).isEqualTo("Setup token has expired");
            });
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should report a wrong token, not expiry, when an expired setup is called with the wrong token")
    void setupOwner_wrongTokenCheckedBeforeExpiry() {
        ReflectionTestUtils.setField(ownerSetupService, "tokenExpiresAt",
            Instant.now().minus(1, ChronoUnit.MINUTES));

        SetupOwnerRequest request = new SetupOwnerRequest(
            "e1234567@u.nus.edu", "owner_user", "ValidPassword123!"
        );

        assertThatThrownBy(() -> ownerSetupService.setupOwner(request, "wrong-token"))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getReason())
                .isEqualTo("Invalid or missing setup token"));
    }

    @Test
    @DisplayName("Should throw 503 Service Unavailable when OWNER_SETUP_TOKEN_EXPIRES_AT is not configured")
    void setupOwner_throwsServiceUnavailableWhenExpiryNotConfigured() {
        ReflectionTestUtils.setField(ownerSetupService, "tokenExpiresAt", null);

        SetupOwnerRequest request = new SetupOwnerRequest(
            "e1234567@u.nus.edu", "owner_user", "ValidPassword123!"
        );

        assertThatThrownBy(() -> ownerSetupService.setupOwner(request, VALID_SETUP_TOKEN))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex ->
                assertThat(((ResponseStatusException) ex).getStatusCode())
                    .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
            );
    }

    // existing owners

    @Test
    @DisplayName("Should create an OWNER without checking for existing owners")
    void setupOwner_doesNotCheckExistingOwners() {
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        SetupOwnerRequest request = new SetupOwnerRequest(
            "e1234567@u.nus.edu", "owner_user", "ValidPassword123!"
        );

        UserResponse response = ownerSetupService.setupOwner(request, VALID_SETUP_TOKEN);

        assertThat(response.role()).isEqualTo("OWNER");
        verify(userRepository).save(any(User.class));
        verify(userRepository, never()).countByRole(any());
    }

    // checks uniqueness of details

    @Test
    @DisplayName("Should throw 400 when email is already registered")
    void setupOwner_throwsBadRequestOnDuplicateEmail() {
        when(userRepository.existsByEmail("e1234567@u.nus.edu")).thenReturn(true);

        SetupOwnerRequest request = new SetupOwnerRequest(
            "e1234567@u.nus.edu", "owner_user", "ValidPassword123!"
        );

        assertThatThrownBy(() -> ownerSetupService.setupOwner(request, VALID_SETUP_TOKEN))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex ->
                assertThat(((ResponseStatusException) ex).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST)
            );
    }

    @Test
    @DisplayName("Should throw 400 when username is already taken")
    void setupOwner_throwsBadRequestOnDuplicateUsername() {
        when(userRepository.existsByUsername("owner_user")).thenReturn(true);

        SetupOwnerRequest request = new SetupOwnerRequest(
            "e1234567@u.nus.edu", "owner_user", "ValidPassword123!"
        );

        assertThatThrownBy(() -> ownerSetupService.setupOwner(request, VALID_SETUP_TOKEN))
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
        User saved = new User("e1234567@u.nus.edu", "owner_user", "hashed_password", Role.OWNER);
        when(userRepository.save(any(User.class))).thenReturn(saved);

        // Bypass Bean Validation intentionally — service must still normalise
        SetupOwnerRequest request = new SetupOwnerRequest(
            "  E1234567@U.NUS.EDU  ", "owner_user", "ValidPassword123!"
        );

        ownerSetupService.setupOwner(request, VALID_SETUP_TOKEN);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("e1234567@u.nus.edu");
    }

    @Test
    @DisplayName("Should trim leading and trailing whitespace from username before saving")
    void setupOwner_trimsUsernameWhitespace() {
        User saved = new User("e1234567@u.nus.edu", "owner_user", "hashed_password", Role.OWNER);
        when(userRepository.save(any(User.class))).thenReturn(saved);

        SetupOwnerRequest request = new SetupOwnerRequest(
            "e1234567@u.nus.edu", "  owner_user  ", "ValidPassword123!"
        );

        ownerSetupService.setupOwner(request, VALID_SETUP_TOKEN);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("owner_user");
    }

    // --- happy path ---

    @Test
    @DisplayName("Should save user with OWNER role and a bcrypt-hashed password, and return a response with no password field")
    void setupOwner_savesOwnerAndReturnsResponse() {
        when(passwordEncoder.encode("ValidPassword123!")).thenReturn("bcrypt_hash");
        User saved = new User("e1234567@u.nus.edu", "owner_user", "bcrypt_hash", Role.OWNER);
        when(userRepository.save(any(User.class))).thenReturn(saved);

        SetupOwnerRequest request = new SetupOwnerRequest(
            "e1234567@u.nus.edu", "owner_user", "ValidPassword123!"
        );

        UserResponse response = ownerSetupService.setupOwner(request, VALID_SETUP_TOKEN);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        assertThat(captor.getValue().getRole()).isEqualTo(Role.OWNER);
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("bcrypt_hash");
        assertThat(response.role()).isEqualTo("OWNER");
        assertThat(response.email()).isEqualTo("e1234567@u.nus.edu");
    }
}
