/*
AI Assistance Disclosure:
Tool: Claude Code (Opus 5.5), date: 2026-09-26
Scope: Generated unit tests for AdminService (issue #96): role change,
       removal, the owner and self-removal rules, and list parameter
       validation. Search, filtering and sorting run against Postgres in
       AdminControllerTest. Tests use Mockito to isolate the service from
       the database.
Author review: Ryan reviewed and ensured tests run successfully.
*/

package foc.user.service;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import foc.user.dto.UserResponse;
import foc.user.entity.Role;
import foc.user.entity.User;
import foc.user.exception.UserNotFoundException;
import foc.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    private static final Long CALLER_ID = 1L;
    private static final Long TARGET_ID = 2L;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AdminService adminService;

    private User target;

    @BeforeEach
    void createTarget() {
        target = new User("e1234567@u.nus.edu", "student_alex", "hashed_password", Role.USER);
        ReflectionTestUtils.setField(target, "id", TARGET_ID);
    }

    private void targetIsActive() {
        when(userRepository.findByIdAndDeletedAtIsNull(TARGET_ID)).thenReturn(Optional.of(target));
    }

    private static void assertStatus(Throwable thrown, HttpStatus status) {
        assertThat(thrown).isInstanceOf(ResponseStatusException.class);
        assertThat(((ResponseStatusException) thrown).getStatusCode()).isEqualTo(status);
    }

    // role change

    @Test
    @DisplayName("Should promote a USER to ADMIN")
    void changeRole_promotesUser() {
        targetIsActive();
        when(userRepository.save(target)).thenReturn(target);

        UserResponse response = adminService.changeRole(TARGET_ID, Role.ADMIN);

        assertThat(target.getRole()).isEqualTo(Role.ADMIN);
        assertThat(response.role()).isEqualTo("ADMIN");
        verify(userRepository).save(target);
    }

    @Test
    @DisplayName("Should demote an ADMIN to USER, including the caller themselves")
    void changeRole_demotesAdmin() {
        target.setRole(Role.ADMIN);
        targetIsActive();
        when(userRepository.save(target)).thenReturn(target);

        UserResponse response = adminService.changeRole(TARGET_ID, Role.USER);

        assertThat(target.getRole()).isEqualTo(Role.USER);
        assertThat(response.role()).isEqualTo("USER");
    }

    @Test
    @DisplayName("Should return the user unchanged without saving when the role is already held")
    void changeRole_noOp() {
        target.setRole(Role.ADMIN);
        targetIsActive();

        UserResponse response = adminService.changeRole(TARGET_ID, Role.ADMIN);

        assertThat(response.role()).isEqualTo("ADMIN");
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should refuse to grant OWNER")
    void changeRole_ownerRequested() {
        Throwable thrown = catchThrowable(
            () -> adminService.changeRole(TARGET_ID, Role.OWNER));

        assertStatus(thrown, HttpStatus.FORBIDDEN);
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("Should refuse to change an OWNER's role")
    void changeRole_ownerTarget() {
        target.setRole(Role.OWNER);
        targetIsActive();

        Throwable thrown = catchThrowable(
            () -> adminService.changeRole(TARGET_ID, Role.USER));

        assertStatus(thrown, HttpStatus.FORBIDDEN);
        assertThat(target.getRole()).isEqualTo(Role.OWNER);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw UserNotFoundException when the target is missing or deleted")
    void changeRole_notFound() {
        when(userRepository.findByIdAndDeletedAtIsNull(TARGET_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.changeRole(TARGET_ID, Role.ADMIN))
            .isInstanceOf(UserNotFoundException.class);
    }

    // removal

    @Test
    @DisplayName("Should soft delete the target")
    void removeUser_softDeletes() {
        targetIsActive();

        adminService.removeUser(CALLER_ID, TARGET_ID);

        assertThat(target.getDeletedAt()).isNotNull();
        verify(userRepository).save(target);
    }

    @Test
    @DisplayName("Should allow removing another ADMIN")
    void removeUser_otherAdmin() {
        target.setRole(Role.ADMIN);
        targetIsActive();

        adminService.removeUser(CALLER_ID, TARGET_ID);

        assertThat(target.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should refuse removing the caller's own account")
    void removeUser_self() {
        Throwable thrown = catchThrowable(
            () -> adminService.removeUser(TARGET_ID, TARGET_ID));

        assertStatus(thrown, HttpStatus.FORBIDDEN);
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("Should refuse removing an OWNER")
    void removeUser_ownerTarget() {
        target.setRole(Role.OWNER);
        targetIsActive();

        Throwable thrown = catchThrowable(
            () -> adminService.removeUser(CALLER_ID, TARGET_ID));

        assertStatus(thrown, HttpStatus.FORBIDDEN);
        assertThat(target.getDeletedAt()).isNull();
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw UserNotFoundException when removing a missing or deleted user")
    void removeUser_notFound() {
        when(userRepository.findByIdAndDeletedAtIsNull(TARGET_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.removeUser(CALLER_ID, TARGET_ID))
            .isInstanceOf(UserNotFoundException.class);
    }

    // list parameter validation

    @ParameterizedTest
    @ValueSource(ints = {0, 10, 30, 101, -1})
    @DisplayName("Should reject page sizes other than 20, 50 and 100")
    void listUsers_invalidSize(int size) {
        Throwable thrown = catchThrowable(
            () -> adminService.listUsers(null, null, null, 0, size));

        assertStatus(thrown, HttpStatus.BAD_REQUEST);
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("Should reject a negative page number")
    void listUsers_negativePage() {
        Throwable thrown = catchThrowable(
            () -> adminService.listUsers(null, null, null, -1, 100));

        assertStatus(thrown, HttpStatus.BAD_REQUEST);
    }

    @ParameterizedTest
    @ValueSource(strings = {"passwordHash", "deletedAt", "username,sideways", "username,asc,extra", ",asc"})
    @DisplayName("Should reject unknown sort fields and directions")
    void listUsers_invalidSort(String sort) {
        Throwable thrown = catchThrowable(
            () -> adminService.listUsers(null, null, sort, 0, 100));

        assertStatus(thrown, HttpStatus.BAD_REQUEST);
        verifyNoInteractions(userRepository);
    }
}
