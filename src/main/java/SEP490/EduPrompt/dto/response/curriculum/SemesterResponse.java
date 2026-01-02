package SEP490.EduPrompt.dto.response.curriculum;

import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder
public record SemesterResponse(
        UUID id,
        Integer semesterNumber,
        String name,
        List<ChapterResponse> listOfChapter
) {}