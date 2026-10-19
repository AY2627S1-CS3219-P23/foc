/*
AI Assistance Disclosure:
Tool: Claude Code (Fable 5), date: 2026-10-11
Scope: the password-reset routes for issue #94 (F4.2). Separate from
       AuthController (which is sign-up/login/logout) but under the
       same /auth prefix, so SecurityConfig's /auth/** permitAll and
       the gateway's public /api/user/auth/ location cover both
       routes with no config change. The 202 carries no body and no
       timings on purpose: everything observable must be identical
       whether or not the email has an account (owner decision via
       options Q&A; contrast SignupResponse's quoted timings, where
       the caller is creating the account). Errors render through
       ProblemDetailAdvice (validation 400s, the one reset-code 400,
       the mail 502).
Author review: Leong Wei Zhi to review via the PR.
*/

package foc.user.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import foc.user.dto.PasswordResetConfirmRequest;
import foc.user.dto.PasswordResetRequest;
import foc.user.service.PasswordResetService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth/password-reset")
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    public PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    // 202: the request was taken, nothing about the address is confirmed
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void requestReset(@Valid @RequestBody PasswordResetRequest request) {
        passwordResetService.requestReset(request.email());
    }

    @PostMapping(value = "/confirm", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        passwordResetService.confirmReset(request);
    }
}
