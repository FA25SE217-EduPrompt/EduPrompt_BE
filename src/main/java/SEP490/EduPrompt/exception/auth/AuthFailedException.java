package SEP490.EduPrompt.exception.auth;

import SEP490.EduPrompt.exception.BaseException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class AuthFailedException extends BaseException {
    public AuthFailedException() {
        super(AuthExceptionCode.AUTH_FAILED.name(), "Authentication failed", HttpStatus.UNAUTHORIZED);
    }

    public AuthFailedException(String message) {
        super(AuthExceptionCode.AUTH_FAILED.name(), message, HttpStatus.UNAUTHORIZED);
    }
}
