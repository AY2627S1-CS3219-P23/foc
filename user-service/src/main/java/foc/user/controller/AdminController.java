/*
AI Assistance Disclosure:
Tool: Claude Code (Opus 5.5), date: 2026-09-26
Scope: Generated the admin endpoints for issue #96: GET /users (list),
       PATCH /users/{id} (role change) and DELETE /users/{id} (remove).
       Access needs the ADMIN or OWNER authority (URL rules in
       SecurityConfig), which the JWT filter from #91 is expected to map
       from the token's role claim (team decision); until then tests supply
       the authorities directly. Errors are
       problem+json, as in ProfileController.
Author review: Ryan reviewed to ensure it follows the team's decisions.
2026-09-27 (Claude Code, Opus 5.5), PR #135 review: callerId and the
UserNotFoundException handler moved to CallerId / ProblemDetailAdvice,
shared with ProfileController.
2026-09-29 (Claude Code, Opus 5.5), issue #147: includeDeleted query
parameter on GET /users (team decision); invalid bodies now get
ProblemDetailAdvice's per-field reasons like every other controller (team
decision), so only the query-parameter type mismatch stays here.
2026-10-02 (Claude Code, Opus 5.5), issue #154: the ResponseStatusException
and type-mismatch handlers moved to ProblemDetailAdvice.
*/

package foc.user.controller;

import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import foc.user.dto.UpdateRoleRequest;
import foc.user.dto.UserResponse;
import foc.user.entity.Role;
import foc.user.service.AdminService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/users")
// ADMIN/OWNER only: enforced by the URL rules in SecurityConfig
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public PagedModel<UserResponse> listUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + AdminService.DEFAULT_PAGE_SIZE) int size,
            @RequestParam(defaultValue = "false") boolean includeDeleted) {
        return new PagedModel<>(adminService.listUsers(search, role, sort, page, size, includeDeleted));
    }

    @PatchMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public UserResponse changeRole(@PathVariable Long id, @Valid @RequestBody UpdateRoleRequest request) {
        return adminService.changeRole(id, request.role());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeUser(@PathVariable Long id, Authentication authentication) {
        adminService.removeUser(CallerId.from(authentication), id);
    }
}
