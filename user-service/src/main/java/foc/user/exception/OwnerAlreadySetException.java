package foc.user.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class OwnerAlreadySetException extends RuntimeException {
    public OwnerAlreadySetException() {
        super("This setup is disabled. Owner has been set");
    }
}
