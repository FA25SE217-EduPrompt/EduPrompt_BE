package SEP490.EduPrompt.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class ErrorMessage {
    private String code;
    private List<String> message;

    public ErrorMessage(String code, String message) {
        this.code = code;
        this.message = List.of(message);
    }
}
