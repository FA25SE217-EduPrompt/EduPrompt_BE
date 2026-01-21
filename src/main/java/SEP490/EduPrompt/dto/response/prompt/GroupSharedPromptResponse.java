package SEP490.EduPrompt.dto.response.prompt;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record GroupSharedPromptResponse (
        UUID id,
        String title,
        String description,
        String outputFormat,
        String visibility,
        String fullName, // Owner's full name
        UUID collectionId,
        UUID groupId, // Added field for group name
        Instant createdAt,
        Instant updatedAt
){
}
