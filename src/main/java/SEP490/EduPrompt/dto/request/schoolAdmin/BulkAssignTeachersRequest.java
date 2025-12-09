package SEP490.EduPrompt.dto.request.schoolAdmin;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BulkAssignTeachersRequest(
        @NotEmpty(message = "Email list cannot be empty")
        @Size(max = 50, message = "Maximum 50 emails allowed per request")
        List<String> emails
) {
}
