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
*/

package foc.user.controller;

import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import foc.user.dto.UpdateRoleRequest;
import foc.user.dto.UserResponse;
import foc.user.entity.Role;
import foc.user.exception.UserNotFoundException;
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
            @RequestParam(defaultValue = "" + AdminService.DEFAULT_PAGE_SIZE) int size) {
        return new PagedModel<>(adminService.listUsers(search, role, sort, page, size));
    }

    @PatchMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public UserResponse changeRole(@PathVariable Long id, @Valid @RequestBody UpdateRoleRequest request) {
        return adminService.changeRole(id, request.role());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeUser(@PathVariable Long id, Authentication authentication) {
        adminService.removeUser(callerId(authentication), id);
    }

    // the web client reads RFC 9457 problem+json error bodies

    @ExceptionHandler(UserNotFoundException.class)
    public ProblemDetail handleUserNotFound(UserNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    // blocked actions (403) and invalid list parameters (400)
    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail handleResponseStatus(ResponseStatusException e) {
        return e.getBody();
    }

    // unknown role in the query or body, non-numeric id, missing role in the body
    @ExceptionHandler({
        MethodArgumentTypeMismatchException.class,
        HttpMessageNotReadableException.class,
        MethodArgumentNotValidException.class
    })
    public ProblemDetail handleBadInput(Exception e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request parameter or body");
    }

    // the principal name carries the caller's user id until JWT auth (#91);
    // a non-numeric name is treated as unauthenticated rather than a 500
    private static Long callerId(Authentication authentication) {
        try {
            return Long.valueOf(authentication.getName());
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
    }
}
