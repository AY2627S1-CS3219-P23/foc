/*
AI Assistance Disclosure:
Tool: Claude Code (Opus 5.5), date: 2026-09-23
Scope: Generated controller tests for GET /users/me and GET /users/{id}
       (issue #95) against a Testcontainers Postgres, following
       OwnerSetupControllerTest. The caller is mocked with the user id as
       the principal name until JWT auth (#90/#91) lands.
Author review: Ryan reviewed and ensured tests run successfully.
*/

package foc.user.controller;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import foc.user.entity.Role;
import foc.user.entity.User;
import foc.user.repository.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ProfileControllerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

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
            .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("GET /users/me should be rejected without credentials")
    void getOwnProfile_unauthenticated() throws Exception {
        mockMvc.perform(get("/users/me"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /users/me should return 404 when the caller's account is deleted")
    void getOwnProfile_deletedCaller() throws Exception {
        User deleted = saveDeletedUser();

        mockMvc.perform(get("/users/me").with(user(deleted.getId().toString())))
            .andExpect(status().isNotFound());
    }

    // public profile

    @Test
    @DisplayName("GET /users/{id} should return only username and joined date")
    void getPublicProfile_success() throws Exception {
        mockMvc.perform(get("/users/{id}", alex.getId()).with(user("999")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("student_alex"))
            .andExpect(jsonPath("$.createdAt").exists())
            .andExpect(jsonPath("$.email").doesNotExist())
            .andExpect(jsonPath("$.role").doesNotExist())
            .andExpect(jsonPath("$.id").doesNotExist());
    }

    @Test
    @DisplayName("GET /users/{id} should return 404 for an unknown user")
    void getPublicProfile_unknownUser() throws Exception {
        mockMvc.perform(get("/users/{id}", alex.getId() + 1000).with(user("999")))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /users/{id} should return 404 for a soft-deleted user")
    void getPublicProfile_deletedUser() throws Exception {
        User deleted = saveDeletedUser();

        mockMvc.perform(get("/users/{id}", deleted.getId()).with(user("999")))
            .andExpect(status().isNotFound());
    }
}
