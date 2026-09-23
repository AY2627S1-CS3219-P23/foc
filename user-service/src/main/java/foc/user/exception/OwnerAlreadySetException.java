/*
AI Assistance Disclosure:
Tool: Claude (Sonnet 5), date: 2026-09-22
Scope: Generated 409 Conflict exception for repeated owner setup.
Author review: Ryan validated that the endpoint logic matches the feature design.
*/

package foc.user.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class OwnerAlreadySetException extends RuntimeException {
    public OwnerAlreadySetException() {
        super("This setup is disabled. Owner has been set");
    }
}
