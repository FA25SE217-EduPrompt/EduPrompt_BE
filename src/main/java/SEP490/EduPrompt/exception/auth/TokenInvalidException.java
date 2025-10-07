package SEP490.EduPrompt.exception.auth;

import SEP490.EduPrompt.exception.BaseException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class TokenInvalidException extends BaseException {
    public TokenInvalidException(String message) {
        super(
                AuthExceptionCode.INVALID_TOKEN.name(),
                message,
                HttpStatus.UNAUTHORIZED);
    }
}
