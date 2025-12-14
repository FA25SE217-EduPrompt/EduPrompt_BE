package SEP490.EduPrompt.service.school;

import SEP490.EduPrompt.dto.response.school.SchoolResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface SchoolService {
    Page<SchoolResponse> getAllSchools(Pageable pageable);

    SchoolResponse getSchoolById(UUID schoolId);

    SchoolResponse getSchoolByUserId(UUID userId);
}
