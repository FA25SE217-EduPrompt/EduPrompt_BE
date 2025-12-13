package SEP490.EduPrompt.repo;

import SEP490.EduPrompt.model.TeacherTokenUsageLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TeacherTokenUsageLogRepository extends JpaRepository<TeacherTokenUsageLog, UUID> {
    List<TeacherTokenUsageLog> findBySchoolSubscriptionId(UUID schoolSubscriptionId);

    List<TeacherTokenUsageLog> findBySchoolSubscriptionIdAndUserId(UUID schoolSubscriptionId, UUID userId);

    Page<TeacherTokenUsageLog> findBySchoolSubscriptionId(UUID schoolSubscriptionId, Pageable pageable);

    Page<TeacherTokenUsageLog> findBySchoolSubscriptionIdAndUserId(UUID schoolSubscriptionId, UUID userId, Pageable pageable);
}
