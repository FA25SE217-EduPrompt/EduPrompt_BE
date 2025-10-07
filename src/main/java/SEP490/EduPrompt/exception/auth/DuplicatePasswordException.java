package SEP490.EduPrompt.exception.auth;

import SEP490.EduPrompt.exception.BaseException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class DuplicatePasswordException extends BaseException {
    public DuplicatePasswordException(String message) {
        super(AuthExceptionCode.INVALID_INPUT.name(), "Duplicate password", HttpStatus.CONFLICT);
    }
}
