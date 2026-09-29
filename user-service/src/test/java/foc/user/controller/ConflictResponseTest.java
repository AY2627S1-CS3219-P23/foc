/*
AI Assistance Disclosure:
Tool: Claude Code (Opus 5.5), date: 2026-09-27
Scope: Generated tests for the response when a save loses an optimistic
       locking race (PR #135 review). 409 problem+json per team decision.
       The services are mocked to throw, since a real race can't be timed
       through MockMvc; UserOptimisticLockTest covers the locking itself.
       2026-09-29 (Claude Code, Opus 5.5), PR #141 review: a lost race is
       401 on login only; sign-up keeps the 409.
       2026-09-29 (Claude Code, Opus 5), issue #146: that 401 now carries
       the wrong-password message without a countdown.
Author review: Leong Wei Zhi to review via the PR.
*/

package foc.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import foc.user.PostgresTestContainer;
import foc.user.entity.User;
import foc.user.service.AdminService;
import foc.user.service.AuthService;
import foc.user.service.ProfileService;

@SpringBootTest
@AutoConfigureMockMvc
class ConflictResponseTest extends PostgresTestContainer {

    private static final String DETAIL = "The account was changed by another request; reload and try again";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminService adminService;

    @MockitoBean
    private ProfileService profileService;

    @MockitoBean
    private AuthService authService;

    private static ObjectOptimisticLockingFailureException conflict() {
        return new ObjectOptimisticLockingFailureException(User.class, 5L);
    }

    @Test
    @DisplayName("PATCH /users/{id} should return 409 problem+json when the save loses a race")
    void changeRoleConflict() throws Exception {
        when(adminService.changeRole(any(), any())).thenThrow(conflict());

        mockMvc.perform(patch("/users/{id}", 5).with(user("1").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
            .andExpect(status().isConflict())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.detail").value(DETAIL));
    }

    @Test
    @DisplayName("DELETE /users/{id} should return 409 problem+json when the save loses a race")
    void removeUserConflict() throws Exception {
        doThrow(conflict()).when(adminService).removeUser(any(), any());

        mockMvc.perform(delete("/users/{id}", 5).with(user("1").roles("ADMIN")))
            .andExpect(status().isConflict())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.detail").value(DETAIL));
    }

    @Test
    @DisplayName("DELETE /users/me should return 409 problem+json when the save loses a race")
    void deleteOwnAccountConflict() throws Exception {
        doThrow(conflict()).when(profileService).deleteOwnAccount(any());

        mockMvc.perform(delete("/users/me").with(user("5")))
            .andExpect(status().isConflict())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.detail").value(DETAIL));
    }

    @Test
    @DisplayName("POST /auth/login should answer a lost race like any failed login (401)")
    void loginConflict() throws Exception {
        when(authService.login(any())).thenThrow(conflict());

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"usernameOrEmail\":\"student_alex\",\"password\":\"Password1234\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            // no tally: the attempt that lost the race can't quote a count
            .andExpect(jsonPath("$.detail").value("Incorrect password."));
    }

    @Test
    @DisplayName("POST /auth/signup should keep the 409 problem+json when the save loses a race")
    void signupConflict() throws Exception {
        when(authService.signup(any())).thenThrow(conflict());

        mockMvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"e1234567@u.nus.edu\",\"username\":\"student_alex\","
                    + "\"password\":\"Password1234\"}"))
            .andExpect(status().isConflict())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.detail").value(DETAIL));
    }
}
