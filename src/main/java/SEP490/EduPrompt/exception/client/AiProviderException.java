package SEP490.EduPrompt.exception.client;

import SEP490.EduPrompt.exception.BaseException;
import SEP490.EduPrompt.exception.generic.ExceptionCode;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;

@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class AiProviderException extends BaseException {
    public AiProviderException(String message) {
        super(ExceptionCode.AI_PROVIDER_ERROR.name(), message, HttpStatus.SERVICE_UNAVAILABLE);
    }
    public AiProviderException(String message, Exception e) {
        super(ExceptionCode.AI_PROVIDER_ERROR.name(), List.of(message, e.getMessage()), HttpStatus.SERVICE_UNAVAILABLE);
    }
}
