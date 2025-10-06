package SEP490.EduPrompt.exception.auth;

import SEP490.EduPrompt.exception.BaseException;
import org.springframework.http.HttpStatus;

public class EmailAlreadyExistedException extends BaseException {
    public EmailAlreadyExistedException() {
        super(AuthExceptionCode.ALREADY_EXISTED.name(), "email is already existed ", HttpStatus.BAD_REQUEST);
    }
}
