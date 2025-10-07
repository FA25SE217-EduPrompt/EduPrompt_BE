package SEP490.EduPrompt.exception;

import SEP490.EduPrompt.exception.auth.AuthExceptionCode;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class TokenInvalidException extends BaseException {
    public TokenInvalidException(String message) {
        super(
                AuthExceptionCode.INVALID_INPUT.name(),
                message,
                HttpStatus.BAD_REQUEST);
    }
}
