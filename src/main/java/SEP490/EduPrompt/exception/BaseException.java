package SEP490.EduPrompt.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.List;

@Getter
public class BaseException extends RuntimeException {
    private static final HttpStatus DEFAULT_STATUS = HttpStatus.INTERNAL_SERVER_ERROR;

    private final String code;
    private final List<String> messages;
    private final HttpStatus status;

    public BaseException(String code, String message) {
        super(message);
        this.code = code;
        this.messages = List.of(message);
        this.status = DEFAULT_STATUS;
    }

    public BaseException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.messages = List.of(message);
        this.status = status;
    }

    public BaseException(String code, List<String> messages) {
        super(String.join("; ", messages));
        this.code = code;
        this.messages = messages;
        this.status = DEFAULT_STATUS;
    }

    public BaseException(String code, List<String> messages, HttpStatus status) {
        super(String.join("; ", messages));
        this.code = code;
        this.messages = messages;
        this.status = status;
    }
}
