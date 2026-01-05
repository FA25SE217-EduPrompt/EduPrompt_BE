package SEP490.EduPrompt.dto.response.curriculum;

import lombok.Builder;

import java.util.List;

@Builder
public record CurriculumResponse(
        List<SubjectResponse> subjects,
        List<GradeLevelResponse> gradeLevels,
        List<SemesterResponse> semesters
) {
}
