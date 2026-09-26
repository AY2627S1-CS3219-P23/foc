/*
AI Assistance Disclosure:
Tool: Claude Code (Opus 5.5), date: 2026-09-23
Scope: Generated unit tests for ProfileService (issue #95) covering own and
       public profile lookups and the not-found path. Tests use Mockito to
       isolate the service from the database.
Author review: Ryan reviewed and ensured tests run successfully.
2026-09-25 (Claude Code, Opus 5.5), PR #131 review: own-profile test asserts
       the full response, including id and createdAt.
2026-09-27 (Claude Code, Fable 5), issue #93: deleteOwnAccount cases added
       (soft delete stamps deleted_at; missing/deleted account throws
       without saving).
*/

package foc.user.service;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import foc.user.dto.PublicProfileResponse;
import foc.user.dto.UserResponse;
import foc.user.entity.Role;
import foc.user.entity.User;
import foc.user.exception.UserNotFoundException;
import foc.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ProfileService profileService;

    private final User user = new User("e1234567@u.nus.edu", "student_alex", "hashed_password", Role.USER);

    // own profile

    @Test
    @DisplayName("Should return id, email, username, role and joined date for own profile")
    void getOwnProfile_returnsAccountDetails() {
        ReflectionTestUtils.setField(user, "id", 1L);
        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));

        UserResponse response = profileService.getOwnProfile(1L);

        assertThat(response).isEqualTo(new UserResponse(
            1L, "e1234567@u.nus.edu", "student_alex", "USER", user.getCreatedAt()));
    }

    @Test
    @DisplayName("Should throw UserNotFoundException when own account is missing or deleted")
    void getOwnProfile_throwsWhenNotFound() {
        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> profileService.getOwnProfile(1L))
            .isInstanceOf(UserNotFoundException.class);
    }

    // delete own account

    @Test
    @DisplayName("Should stamp deleted_at when deleting own account")
    void deleteOwnAccount_softDeletes() {
        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));

        profileService.deleteOwnAccount(1L);

        assertThat(user.isActive()).isFalse();
        assertThat(user.getDeletedAt()).isNotNull();
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("Should throw UserNotFoundException when the account is missing or already deleted")
    void deleteOwnAccount_throwsWhenNotFound() {
        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> profileService.deleteOwnAccount(1L))
            .isInstanceOf(UserNotFoundException.class);
        verify(userRepository, never()).save(any());
    }

    // public profile

    @Test
    @DisplayName("Should return only the username for public profile")
    void getPublicProfile_returnsUsernameOnly() {
        when(userRepository.findByIdAndDeletedAtIsNull(2L)).thenReturn(Optional.of(user));

        PublicProfileResponse response = profileService.getPublicProfile(2L);

        assertThat(response.username()).isEqualTo("student_alex");
    }

    @Test
    @DisplayName("Should throw UserNotFoundException when public profile user is missing or deleted")
    void getPublicProfile_throwsWhenNotFound() {
        when(userRepository.findByIdAndDeletedAtIsNull(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> profileService.getPublicProfile(2L))
            .isInstanceOf(UserNotFoundException.class);
    }
}
