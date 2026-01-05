package SEP490.EduPrompt.exception.client;

import SEP490.EduPrompt.exception.BaseException;
import SEP490.EduPrompt.exception.generic.ExceptionCode;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;

@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class FileUploadFailException extends BaseException {
    public  FileUploadFailException(String message) {
        super(ExceptionCode.FILE_UPLOAD_FAILED.name(), message, HttpStatus.SERVICE_UNAVAILABLE);
    }
    public  FileUploadFailException(String message, Exception e) {
        super(ExceptionCode.FILE_UPLOAD_FAILED.name(), List.of(message, e.getMessage()), HttpStatus.SERVICE_UNAVAILABLE);
    }
}
