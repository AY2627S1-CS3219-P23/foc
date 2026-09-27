/*
AI Assistance Disclosure:
Tool: Claude Code (Opus 5.5), date: 2026-09-27
Scope: Generated tests for the response when a save loses an optimistic
       locking race (PR #135 review). 409 problem+json per team decision.
       The services are mocked to throw, since a real race can't be timed
       through MockMvc; UserOptimisticLockTest covers the locking itself.
Author review: Ryan to review via the PR.
*/

package foc.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
}
