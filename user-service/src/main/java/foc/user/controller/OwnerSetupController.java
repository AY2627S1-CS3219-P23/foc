/*
AI Assistance Disclosure:
Tool: Claude (Sonnet 5), date: 2026-09-22
Scope: Generated POST /auth/setup-owner endpoint.
       2026-09-23 (Claude, Sonnet 4.6): X-Setup-Token header added for the
       setup-token gate.
       2026-09-25 (Claude Code, Opus 5.5): setupFirstOwner renamed to
       setupOwner, since setup is no longer limited to the first owner.
       2026-09-25 (Claude Code, Opus 5.5): successful setups are logged
       (new owner id and email, caller IP; never the token).
Author review: Ryan validated that the endpoint logic matches the feature design.
*/

package foc.user.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
public class OwnerSetupController {

    private static final Logger log = LoggerFactory.getLogger(OwnerSetupController.class);

    private final OwnerSetupService ownerSetupService;

    public OwnerSetupController(OwnerSetupService ownerSetupService) {
        this.ownerSetupService = ownerSetupService;
    }

    @PostMapping(value = "/setup-owner", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UserResponse> setupOwner(
            @Valid @RequestBody SetupOwnerRequest request,
            @RequestHeader(value = "X-Setup-Token", required = false, defaultValue = "") String setupToken,
            HttpServletRequest httpRequest) {
        UserResponse response = ownerSetupService.setupOwner(request, setupToken);
        // audit record of the setup; logged after the service's transaction has committed
        log.info("Owner created via setup: userId={}, email={}, remoteAddr={}",
            response.id(), response.email(), httpRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}