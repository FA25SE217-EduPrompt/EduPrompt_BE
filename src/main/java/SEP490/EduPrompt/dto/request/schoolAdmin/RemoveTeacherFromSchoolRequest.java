package SEP490.EduPrompt.dto.request.schoolAdmin;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.util.UUID;

@Builder
public record RemoveTeacherFromSchoolRequest(
        @NotNull UUID teacherId
) {
}