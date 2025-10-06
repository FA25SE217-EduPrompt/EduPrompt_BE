package SEP490.EduPrompt.exception.auth;

import SEP490.EduPrompt.exception.BaseException;
import org.springframework.http.HttpStatus;


public class AuthFailedException extends BaseException {
    public AuthFailedException() {
        super(AuthExceptionCode.AUTH_FAILED.name(), "Invalid email or password", HttpStatus.UNAUTHORIZED);
    }

    public AuthFailedException(String message) {
        super(AuthExceptionCode.AUTH_FAILED.name(), message, HttpStatus.UNAUTHORIZED);
    }
}
