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
2026-09-27 (Claude Code, Opus 5.5), PR #135 review: the repository mock
       calls real default methods, so getActiveUser uses the stubbed finder.
2026-09-29 (Claude Code, Opus 5.5), issue #147: UserResponse gained
deletedAt (null for an active account).
2026-10-11 (Claude Code, Fable 5), issue #94: recoverOwnAccount cases
(restore inside the window, idempotent on active, gone past the window).
The service is built by hand now — @InjectMocks passed 0 for the new
retention-days knob, which made every deleted account "past window".
2026-10-31 (Claude Code, Fable 5), issue #280: public-profile test now
asserts the full record (username + joinedAt). Author review: pending.
*/

package foc.user.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
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

    // runs real default methods (getActiveUser) against the stubbed finders
    @Mock(answer = Answers.CALLS_REAL_METHODS)
    private UserRepository userRepository;

    private ProfileService profileService;

    @BeforeEach
    void buildService() {
        // 30 days, the main yaml's default retention window
        profileService = new ProfileService(userRepository, 30);
    }

    private final User user = new User("e1234567@u.nus.edu", "student_alex", "hashed_password", Role.USER);

    // own profile

    @Test
    @DisplayName("Should return id, email, username, role and joined date for own profile")
    void getOwnProfile_returnsAccountDetails() {
        ReflectionTestUtils.setField(user, "id", 1L);
        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));

        UserResponse response = profileService.getOwnProfile(1L);

        assertThat(response).isEqualTo(new UserResponse(
            1L, "e1234567@u.nus.edu", "student_alex", "USER", user.getCreatedAt(), null));
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

    // recover own account (issue #94)

    @Test
    @DisplayName("Should clear deleted_at when recovering inside the window")
    void recoverOwnAccount_restores() {
        user.softDelete(Instant.now().minus(10, ChronoUnit.DAYS));
        ReflectionTestUtils.setField(user, "id", 1L);
        when(userRepository.findWithLockById(1L)).thenReturn(Optional.of(user));

        UserResponse response = profileService.recoverOwnAccount(1L);

        assertThat(user.isActive()).isTrue();
        assertThat(response.deletedAt()).isNull();
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("Should answer an active caller idempotently, without writing")
    void recoverOwnAccount_idempotentWhenActive() {
        ReflectionTestUtils.setField(user, "id", 1L);
        when(userRepository.findWithLockById(1L)).thenReturn(Optional.of(user));

        UserResponse response = profileService.recoverOwnAccount(1L);

        assertThat(response.username()).isEqualTo("student_alex");
        // no write: an unconditional save bumps @Version for nothing
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should answer as gone past the retention window")
    void recoverOwnAccount_goneAfterWindow() {
        user.softDelete(Instant.now().minus(31, ChronoUnit.DAYS));
        when(userRepository.findWithLockById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> profileService.recoverOwnAccount(1L))
            .isInstanceOf(UserNotFoundException.class);
        assertThat(user.isActive()).isFalse();
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should answer as gone when the account was purged")
    void recoverOwnAccount_goneWhenMissing() {
        when(userRepository.findWithLockById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> profileService.recoverOwnAccount(1L))
            .isInstanceOf(UserNotFoundException.class);
    }

    // public profile

    @Test
    @DisplayName("Should return the username and joined date for public profile")
    void getPublicProfile_returnsUsernameAndJoinedAt() {
        Instant joined = Instant.parse("2026-01-15T08:30:00Z");
        ReflectionTestUtils.setField(user, "createdAt", joined);
        when(userRepository.findByIdAndDeletedAtIsNull(2L)).thenReturn(Optional.of(user));

        PublicProfileResponse response = profileService.getPublicProfile(2L);

        assertThat(response).isEqualTo(new PublicProfileResponse("student_alex", joined));
    }

    @Test
    @DisplayName("Should throw UserNotFoundException when public profile user is missing or deleted")
    void getPublicProfile_throwsWhenNotFound() {
        when(userRepository.findByIdAndDeletedAtIsNull(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> profileService.getPublicProfile(2L))
            .isInstanceOf(UserNotFoundException.class);
    }
}
