package SEP490.EduPrompt.exception.auth;

import SEP490.EduPrompt.exception.BaseException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class UserVerifiedException extends BaseException {
    public UserVerifiedException() {
        super(AuthExceptionCode.ALREADY_VERIFIED.name(), "Email already verified", HttpStatus.CONFLICT);
    }

    public UserVerifiedException(String message) {
        super(AuthExceptionCode.ALREADY_VERIFIED.name(), message, HttpStatus.CONFLICT);
    }
}
