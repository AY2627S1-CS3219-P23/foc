/*
AI Assistance Disclosure:
Tool: Claude Code (Opus 5.5), date: 2026-09-23
Scope: Generated GET /users/me and GET /users/{id} for issue #95. The caller's id is read from the
       authenticated principal's name (the JWT subject once #90/#91 land).
Author review: Ryan reviewed to ensure that it follows the team's decision for the endpoints.
2026-09-25 (Claude Code, Opus 5.5), PR #131 review: non-numeric principal names return 401
instead of a 500; UserNotFoundException rendered as problem+json for the web client.
*/

package foc.user.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import foc.user.dto.PublicProfileResponse;
import foc.user.dto.UserResponse;
import foc.user.exception.UserNotFoundException;
import foc.user.service.ProfileService;

@RestController
@RequestMapping("/users")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping(value = "/me", produces = MediaType.APPLICATION_JSON_VALUE)
    public UserResponse getOwnProfile(Authentication authentication) {
        return profileService.getOwnProfile(callerId(authentication));
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public PublicProfileResponse getPublicProfile(@PathVariable Long id) {
        return profileService.getPublicProfile(id);
    }

    // the web client reads RFC 9457 problem+json error bodies
    @ExceptionHandler(UserNotFoundException.class)
    public ProblemDetail handleUserNotFound(UserNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
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
