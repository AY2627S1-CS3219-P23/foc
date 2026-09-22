/*
AI Assistance Disclosure:
Tool: Claude (Sonnet 4.6), date: 2026-09-22
Scope: Generated unit tests for OwnerSetupService covering owner guard,
       duplicate checks, email/username normalisation, and the happy path.
       Tests use Mockito to isolate the service from the database.
Author review: Ryan to validate test assertions match intended behaviour.
*/

package foc.user.service;

import foc.user.dto.SetupOwnerRequest;
import foc.user.dto.UserResponse;
import foc.user.entity.User;
import foc.user.exception.OwnerAlreadySetException;
import foc.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OwnerSetupServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private OwnerSetupService ownerSetupService;

    @BeforeEach
    void setUpDefaultStubs() {
        when(userRepository.countByRole("OWNER")).thenReturn(0L);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed_password");
    }

    // --- owner guard ---

    @Test
    @DisplayName("Should throw OwnerAlreadySetException when an owner already exists")
    void setupFirstOwner_throwsWhenOwnerAlreadyExists() {
        when(userRepository.countByRole("OWNER")).thenReturn(1L);

        SetupOwnerRequest request = new SetupOwnerRequest(
            "e1234567@u.nus.edu", "owner_user", "ValidPassword123!"
        );

        assertThatThrownBy(() -> ownerSetupService.setupFirstOwner(request))
            .isInstanceOf(OwnerAlreadySetException.class);
    }

    // --- uniqueness checks ---

    @Test
    @DisplayName("Should throw 400 when email is already registered")
    void setupFirstOwner_throwsBadRequestOnDuplicateEmail() {
        when(userRepository.existsByEmail("e1234567@u.nus.edu")).thenReturn(true);

        SetupOwnerRequest request = new SetupOwnerRequest(
            "e1234567@u.nus.edu", "owner_user", "ValidPassword123!"
        );

        assertThatThrownBy(() -> ownerSetupService.setupFirstOwner(request))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex ->
                assertThat(((ResponseStatusException) ex).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST)
            );
    }

    @Test
    @DisplayName("Should throw 400 when username is already taken")
    void setupFirstOwner_throwsBadRequestOnDuplicateUsername() {
        when(userRepository.existsByUsername("owner_user")).thenReturn(true);

        SetupOwnerRequest request = new SetupOwnerRequest(
            "e1234567@u.nus.edu", "owner_user", "ValidPassword123!"
        );

        assertThatThrownBy(() -> ownerSetupService.setupFirstOwner(request))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex ->
                assertThat(((ResponseStatusException) ex).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST)
            );
    }

    // --- normalisation ---

    @Test
    @DisplayName("Should normalise email to lowercase before saving")
    void setupFirstOwner_normalisesEmailToLowercase() {
        User saved = new User("e1234567@u.nus.edu", "owner_user", "hashed_password", "OWNER");
        when(userRepository.save(any(User.class))).thenReturn(saved);

        // Bypass Bean Validation intentionally — service must still normalise
        SetupOwnerRequest request = new SetupOwnerRequest(
            "  E1234567@U.NUS.EDU  ", "owner_user", "ValidPassword123!"
        );

        ownerSetupService.setupFirstOwner(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("e1234567@u.nus.edu");
    }

    @Test
    @DisplayName("Should trim leading and trailing whitespace from username before saving")
    void setupFirstOwner_trimsUsernameWhitespace() {
        User saved = new User("e1234567@u.nus.edu", "owner_user", "hashed_password", "OWNER");
        when(userRepository.save(any(User.class))).thenReturn(saved);

        SetupOwnerRequest request = new SetupOwnerRequest(
            "e1234567@u.nus.edu", "  owner_user  ", "ValidPassword123!"
        );

        ownerSetupService.setupFirstOwner(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("owner_user");
    }

    // --- happy path ---

    @Test
    @DisplayName("Should save user with OWNER role and a bcrypt-hashed password, and return a response with no password field")
    void setupFirstOwner_savesOwnerAndReturnsResponse() {
        when(passwordEncoder.encode("ValidPassword123!")).thenReturn("bcrypt_hash");
        User saved = new User("e1234567@u.nus.edu", "owner_user", "bcrypt_hash", "OWNER");
        when(userRepository.save(any(User.class))).thenReturn(saved);

        SetupOwnerRequest request = new SetupOwnerRequest(
            "e1234567@u.nus.edu", "owner_user", "ValidPassword123!"
        );

        UserResponse response = ownerSetupService.setupFirstOwner(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        assertThat(captor.getValue().getRole()).isEqualTo("OWNER");
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("bcrypt_hash");
        assertThat(response.role()).isEqualTo("OWNER");
        assertThat(response.email()).isEqualTo("e1234567@u.nus.edu");
    }
}
