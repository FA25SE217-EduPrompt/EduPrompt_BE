package SEP490.EduPrompt.exception.auth;

import SEP490.EduPrompt.exception.BaseException;
import org.springframework.http.HttpStatus;

public class UserNotVerifiedException extends BaseException {
    public UserNotVerifiedException() {
        super(AuthExceptionCode.USER_NOT_VERIFIED.name(), "User is either inactive or not verified", HttpStatus.FORBIDDEN);
    }
}

