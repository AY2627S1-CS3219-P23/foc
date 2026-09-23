/*
AI Assistance Disclosure:
Tool: Claude Code (Opus 5.5), date: 2026-09-23
Scope: Generated GET /users/me and GET /users/{id} for issue #95, with
       paths chosen by Ryan. The caller's id is read from the
       authenticated principal's name (the JWT subject once #90/#91 land).
Author review: pending (Ryan to review before merge).
*/

package foc.user.controller;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
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

    // the principal name carries the caller's user id
    @GetMapping(value = "/me", produces = MediaType.APPLICATION_JSON_VALUE)
    public UserResponse getOwnProfile(Authentication authentication) {
        return profileService.getOwnProfile(Long.valueOf(authentication.getName()));
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public PublicProfileResponse getPublicProfile(@PathVariable Long id) {
        return profileService.getPublicProfile(id);
    }
}
