package SEP490.EduPrompt.exception.auth;

import SEP490.EduPrompt.exception.BaseException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class InvalidCredentialsException extends BaseException {
    public InvalidCredentialsException() {
        super(AuthExceptionCode.INVALID_CREDENTIALS.name(), "Invalid email", HttpStatus.UNAUTHORIZED);
    }

    public InvalidCredentialsException(String message) {
        super(AuthExceptionCode.INVALID_CREDENTIALS.name(), message, HttpStatus.UNAUTHORIZED);
    }
}
