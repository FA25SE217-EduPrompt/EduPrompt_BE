package SEP490.EduPrompt.dto.response.curriculum;

import lombok.Builder;
import java.util.UUID;

@Builder
public record PromptLessonResponse(
        UUID id,
        UUID userId,
        UUID collectionId,
        String title,
        String description,
        String instruction,
        String context,
        String inputExample,
        String outputFormat,
        String constraints,
        String visibility,
        Double avgRating,
        UUID lessonId
) {}