package SEP490.EduPrompt.dto.request.school;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.util.UUID;

@Builder
public record JoinSchoolRequest(
        @NotNull(message = "school id must not be null")
        UUID schoolId
) {
}
