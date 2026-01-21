package SEP490.EduPrompt.repo;

import SEP490.EduPrompt.model.TeacherTokenUsageLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface TeacherTokenUsageLogRepository extends JpaRepository<TeacherTokenUsageLog, UUID> {
    List<TeacherTokenUsageLog> findBySchoolSubscriptionId(UUID schoolSubscriptionId);

    List<TeacherTokenUsageLog> findBySchoolSubscriptionIdAndUserId(UUID schoolSubscriptionId, UUID userId);

    Page<TeacherTokenUsageLog> findBySchoolSubscriptionId(UUID schoolSubscriptionId, Pageable pageable);

    Page<TeacherTokenUsageLog> findBySchoolSubscriptionIdAndUserId(UUID schoolSubscriptionId, UUID userId, Pageable pageable);

    Page<TeacherTokenUsageLog> findBySchoolSubscriptionIdOrderByUsedAtDesc(UUID subscriptionId, Pageable pageable);

    Page<TeacherTokenUsageLog> findAllByOrderByUsedAtDesc(Pageable pageable);

    @Query(value = """
            SELECT
        CAST(EXTRACT(YEAR FROM used_at) AS INTEGER) AS year,
        CAST(EXTRACT(MONTH FROM used_at) AS INTEGER) AS month,
        SUM(tokens_used) AS total_tokens,
        COUNT(*) AS entry_count,
        COUNT(DISTINCT user_id) AS unique_teachers
    FROM teacher_token_usage_log
    GROUP BY EXTRACT(YEAR FROM used_at), EXTRACT(MONTH FROM used_at)
    ORDER BY year DESC, month DESC
    """, nativeQuery = true)
    List<Object[]> getMonthlyUsageSummaryRaw();
}
