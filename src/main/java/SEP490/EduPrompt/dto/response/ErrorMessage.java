package SEP490.EduPrompt.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.http.HttpStatus;

import java.util.List;

@Data
@AllArgsConstructor
public class ErrorMessage {
    private static final HttpStatus DEFAULT_STATUS = HttpStatus.BAD_REQUEST;

    private String code;
    private List<String> message;
    private HttpStatus status;

    public ErrorMessage(String code, String message) {
        this.code = code;
        this.message = List.of(message);
        this.status = DEFAULT_STATUS;
    }

    public ErrorMessage(String code, List<String> message) {
        this.code = code;
        this.message = message;
        this.status = DEFAULT_STATUS;
    }
}
