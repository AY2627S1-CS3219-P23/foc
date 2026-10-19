/*
AI Assistance Disclosure:
Tool: Claude (Sonnet 5), date: 2026-09-22
Scope: Generated POST /auth/setup-owner endpoint.
       2026-09-23 (Claude, Sonnet 4.6): X-Setup-Token header added for the
       setup-token gate.
       2026-09-25 (Claude Code, Opus 5.5): setupFirstOwner renamed to
       setupOwner, since setup is no longer limited to the first owner.
Author review: Ryan validated that the endpoint logic matches the feature design.
*/

package foc.user.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import foc.user.dto.SignupOwnerRequest;
import foc.user.dto.SignupResponse;
import foc.user.dto.SignupVerifyRequest;
import foc.user.dto.UserResponse;
import foc.user.service.OwnerSignupService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
public class OwnerSignupController {

    private final OwnerSignupService ownerSetupService;

    public OwnerSignupController(OwnerSignupService ownerSetupService) {
        this.ownerSetupService = ownerSetupService;
    }

    @PostMapping(value = "/signup-owner", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SignupResponse signup(@Valid @RequestBody SignupOwnerRequest request) {
        return ownerSetupService.signupOwner(request);
    }

    @PostMapping(value = "/signup/verify-owner", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse verifySignup(@Valid @RequestBody SignupVerifyRequest request) {
        return ownerSetupService.verifySignup(request);
    }


}