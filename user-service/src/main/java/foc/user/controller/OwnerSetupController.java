package foc.user.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    public ResponseEntity<UserResponse> setupFirstOwner(@Valid @RequestBody SetupOwnerRequest request) {
        UserResponse response = ownerSetupService.setupFirstOwner(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}