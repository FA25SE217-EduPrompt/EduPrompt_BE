package SEP490.EduPrompt.exception.auth;

import SEP490.EduPrompt.exception.BaseException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class EmailAlreadyExistedException extends BaseException {
    public EmailAlreadyExistedException() {
        super(AuthExceptionCode.ALREADY_EXISTS.name(), "Email already exists", HttpStatus.CONFLICT);
    }
}
