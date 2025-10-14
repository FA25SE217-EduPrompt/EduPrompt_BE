package SEP490.EduPrompt.exception.auth;

import SEP490.EduPrompt.exception.BaseException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class AccessDeniedException extends BaseException {
    public AccessDeniedException(String message) {
        super(AuthExceptionCode.ACCESS_DENIED.name(), message, HttpStatus.FORBIDDEN);
    }

    public AccessDeniedException() {
        super(AuthExceptionCode.ACCESS_DENIED.name(), "You do not have permission to access this asset", HttpStatus.FORBIDDEN);
    }
}
