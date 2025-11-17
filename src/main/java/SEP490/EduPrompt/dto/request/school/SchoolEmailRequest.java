package SEP490.EduPrompt.dto.request.school;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.util.List;

@Builder
public record SchoolEmailRequest(
        @NotEmpty(message = "At least one email is required")
        @Size(max = 50, message = "Maximum 50 emails allowed per request")
        List<String> emails
) {
}
