/*
AI Assistance Disclosure:
Tool: Claude Code (Opus 5.5), date: 2026-09-23
Scope: Generated controller tests for GET /users/me and GET /users/{id}
       (issue #95) against a Testcontainers Postgres, following
       OwnerSetupControllerTest. The caller is mocked with the user id as
       the principal name until JWT auth (#90/#91) lands.
Author review: Ryan reviewed and ensured tests run successfully.
2026-09-25 (Claude Code, Opus 5.5), PR #131 review: container moved to the
       shared PostgresTestContainer base; createdAt, problem+json detail and
       non-numeric principal (401) assertions added; 403 marked provisional.
2026-09-27 (Claude Code, Fable 5), issue #93: DELETE /users/me cases added
       (soft delete, repeat delete 404, unauthenticated).
*/

package foc.user.controller;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import foc.user.PostgresTestContainer;
import foc.user.entity.Role;
import foc.user.entity.User;
import foc.user.repository.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
class ProfileControllerTest extends PostgresTestContainer {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    private User alex;

    @BeforeEach
    void seedUsers() {
        userRepository.deleteAll();
        alex = userRepository.save(
            new User("e1234567@u.nus.edu", "student_alex", "hashed_password", Role.USER));
    }

    private User saveDeletedUser() {
        User deleted = new User("e7654321@u.nus.edu", "gone_user", "hashed_password", Role.USER);
        deleted.setDeletedAt(Instant.now());
        return userRepository.save(deleted);
    }

    // own profile

    @Test
    @DisplayName("GET /users/me should return the caller's username, email and role")
    void getOwnProfile_success() throws Exception {
        mockMvc.perform(get("/users/me").with(user(alex.getId().toString())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(alex.getId()))
            .andExpect(jsonPath("$.username").value("student_alex"))
            .andExpect(jsonPath("$.email").value("e1234567@u.nus.edu"))
            .andExpect(jsonPath("$.role").value("USER"))
            .andExpect(jsonPath("$.createdAt").exists())
            .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("GET /users/me should be rejected without credentials")
    void getOwnProfile_unauthenticated() throws Exception {
        // provisional: 403 is Spring Security's default with no entry point
        // configured; expected to become 401 once JWT auth (#91) adds one
        mockMvc.perform(get("/users/me"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /users/me should return 401 when the principal is not a user id")
    void getOwnProfile_nonNumericPrincipal() throws Exception {
        mockMvc.perform(get("/users/me").with(user("not-a-number")))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /users/me should return 404 when the caller's account is deleted")
    void getOwnProfile_deletedCaller() throws Exception {
        User deleted = saveDeletedUser();

        mockMvc.perform(get("/users/me").with(user(deleted.getId().toString())))
            .andExpect(status().isNotFound());
    }

    // delete own account

    @Test
    @DisplayName("DELETE /users/me should soft-delete the caller's account")
    void deleteOwnAccount_success() throws Exception {
        mockMvc.perform(delete("/users/me").with(user(alex.getId().toString())))
            .andExpect(status().isNoContent());

        User reloaded = userRepository.findById(alex.getId()).orElseThrow();
        assertThat(reloaded.isActive()).isFalse();
        assertThat(reloaded.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("DELETE /users/me should return 404 when the account is already deleted")
    void deleteOwnAccount_repeatDelete() throws Exception {
        mockMvc.perform(delete("/users/me").with(user(alex.getId().toString())))
            .andExpect(status().isNoContent());

        mockMvc.perform(delete("/users/me").with(user(alex.getId().toString())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.detail").value("User not found"));
    }

    @Test
    @DisplayName("DELETE /users/me should be rejected without credentials")
    void deleteOwnAccount_unauthenticated() throws Exception {
        // provisional: 403 is Spring Security's default with no entry point
        // configured; expected to become 401 once JWT auth (#91) adds one
        mockMvc.perform(delete("/users/me"))
            .andExpect(status().isForbidden());

        assertThat(userRepository.findById(alex.getId()).orElseThrow().isActive()).isTrue();
    }

    // public profile

    @Test
    @DisplayName("GET /users/{id} should return only the username")
    void getPublicProfile_success() throws Exception {
        mockMvc.perform(get("/users/{id}", alex.getId()).with(user("999")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("student_alex"))
            .andExpect(jsonPath("$.createdAt").doesNotExist())
            .andExpect(jsonPath("$.email").doesNotExist())
            .andExpect(jsonPath("$.role").doesNotExist())
            .andExpect(jsonPath("$.id").doesNotExist());
    }

    @Test
    @DisplayName("GET /users/{id} should return 404 for an unknown user")
    void getPublicProfile_unknownUser() throws Exception {
        mockMvc.perform(get("/users/{id}", alex.getId() + 1000).with(user("999")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.detail").value("User not found"));
    }

    @Test
    @DisplayName("GET /users/{id} should return 404 for a soft-deleted user")
    void getPublicProfile_deletedUser() throws Exception {
        User deleted = saveDeletedUser();

        mockMvc.perform(get("/users/{id}", deleted.getId()).with(user("999")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.detail").value("User not found"));
    }
}
