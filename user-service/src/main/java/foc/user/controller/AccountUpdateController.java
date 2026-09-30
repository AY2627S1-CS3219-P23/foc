/*
AI Assistance Disclosure:
Tool: Claude Code (Fable 5), date: 2026-09-30
Scope: the account-update routes for issue #92 (requirement F2), design
       doc §5. Contract decided by Leong Wei Zhi via options Q&A
       (2026-09-30); /email/resend is the one addition beyond that list
       (the gate code is consumed when the change parks, so a resend
       can't re-run the PATCH). No local exception handlers: OTP
       failures, uniqueness refusals and validation all render through
       ProblemDetailAdvice with their problem+json type URIs.
Author review: Leong Wei Zhi to review via the PR.
*/

package foc.user.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import foc.user.dto.ChangePasswordRequest;
import foc.user.dto.EmailChangePendingResponse;
import foc.user.dto.UpdateAccountRequest;
import foc.user.dto.UpdateOtpResponse;
import foc.user.dto.UserResponse;
import foc.user.dto.VerifyEmailChangeRequest;
import foc.user.service.AccountUpdateService;
import foc.user.service.AccountUpdateService.UpdateResult;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/users/me")
public class AccountUpdateController {

    private final AccountUpdateService accountUpdateService;

    public AccountUpdateController(AccountUpdateService accountUpdateService) {
        this.accountUpdateService = accountUpdateService;
    }

    // 202, not 200: a code was emailed to the account's current address
    // and nothing has changed yet. Repeating the call resends (a fresh
    // code); there is no separate resend route (sign-up's shape)
    @PostMapping(value = "/otp", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public UpdateOtpResponse requestOtp(Authentication authentication) {
        return accountUpdateService.requestOtp(CallerId.from(authentication));
    }

    // 200 when everything applied; 202 when an email change parked and
    // waits on the new address's code (decision via options Q&A) — the
    // 202 body carries the account as it stands, so a username change in
    // the same PATCH is visible either way
    @PatchMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateAccount(Authentication authentication,
            @Valid @RequestBody UpdateAccountRequest request) {
        UpdateResult result = accountUpdateService.updateAccount(CallerId.from(authentication), request);
        if (result.emailParked()) {
            return ResponseEntity.accepted().body(new EmailChangePendingResponse(
                result.user(), result.pendingEmail(),
                result.expiresInSeconds(), result.resendInSeconds()));
        }
        return ResponseEntity.ok(result.user());
    }

    @PostMapping(value = "/email/verify", consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE)
    public UserResponse verifyEmailChange(Authentication authentication,
            @Valid @RequestBody VerifyEmailChangeRequest request) {
        return accountUpdateService.verifyEmailChange(CallerId.from(authentication), request.code());
    }

    // the gate code was consumed when the change parked, so a resend has
    // its own route: it only re-emails a code to the address the already-
    // gated PATCH chose (same trust as sign-up's repeat-to-resend)
    @PostMapping(value = "/email/resend", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public EmailChangePendingResponse resendEmailChange(Authentication authentication) {
        return accountUpdateService.resendEmailChange(CallerId.from(authentication));
    }

    @PostMapping(value = "/password", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(Authentication authentication,
            @Valid @RequestBody ChangePasswordRequest request) {
        accountUpdateService.changePassword(CallerId.from(authentication), request);
    }
}
