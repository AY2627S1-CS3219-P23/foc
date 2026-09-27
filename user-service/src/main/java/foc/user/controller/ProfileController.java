/*
AI Assistance Disclosure:
Tool: Claude Code (Opus 5.5), date: 2026-09-23
Scope: Generated GET /users/me and GET /users/{id} for issue #95. The caller's id is read from the
       authenticated principal's name (the JWT subject once #90/#91 land).
Author review: Ryan reviewed to ensure that it follows the team's decision for the endpoints.
2026-09-25 (Claude Code, Opus 5.5), PR #131 review: non-numeric principal names return 401
instead of a 500; UserNotFoundException rendered as problem+json for the web client.
2026-09-27 (Claude Code, Fable 5), issue #93: DELETE /users/me added (soft
delete; bearer token only, no body — chosen by Leong Wei Zhi via options Q&A).
2026-09-27 (Claude Code, Opus 5.5), PR #135 review: callerId and the
UserNotFoundException handler moved to CallerId / UserNotFoundAdvice,
shared with AdminController.
*/

package foc.user.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import foc.user.dto.PublicProfileResponse;
import foc.user.dto.UserResponse;
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
        return profileService.getOwnProfile(CallerId.from(authentication));
    }

    // soft delete (design doc §2): 404 if the account is already deleted,
    // matching the other /users/me routes
    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteOwnAccount(Authentication authentication) {
        profileService.deleteOwnAccount(CallerId.from(authentication));
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public PublicProfileResponse getPublicProfile(@PathVariable Long id) {
        return profileService.getPublicProfile(id);
    }
}
