/*
AI Assistance Disclosure:
Tool: Claude (Sonnet 5), date: 2026-09-22
Scope: Generated POST /auth/setup-owner endpoint; X-Setup-Token header
       added for the setup-token gate on 2026-09-23.
Author review: Ryan validated that the endpoint logic matches the feature design.
*/

package foc.user.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import foc.user.dto.SetupOwnerRequest;
import foc.user.dto.UserResponse;
import foc.user.service.OwnerSetupService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
public class OwnerSetupController {

    private final OwnerSetupService ownerSetupService;

    public OwnerSetupController(OwnerSetupService ownerSetupService) {
        this.ownerSetupService = ownerSetupService;
    }

    @PostMapping(value = "/setup-owner", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UserResponse> setupFirstOwner(
            @Valid @RequestBody SetupOwnerRequest request,
            @RequestHeader(value = "X-Setup-Token", required = false, defaultValue = "") String setupToken) {
        UserResponse response = ownerSetupService.setupFirstOwner(request, setupToken);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}