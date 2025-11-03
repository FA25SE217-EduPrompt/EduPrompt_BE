package SEP490.EduPrompt.repo;


import SEP490.EduPrompt.model.PromptUsage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PromptUsageRepository extends JpaRepository<PromptUsage, UUID> {

    Optional<PromptUsage> findByIdempotencyKey(String idempotencyKey);

    @Query("SELECT COUNT(pu) FROM PromptUsage pu " +
            "WHERE pu.userId = :userId " +
            "AND pu.createdAt >= :since")
    int countByUserIdAndCreatedAtAfter(
            @Param("userId") UUID userId,
            @Param("since") LocalDateTime since
    );

    int countByStatus(String usageStatus);

    List<PromptUsage> findByPromptIdOrderByCreatedAtDesc(UUID promptId);

    Page<PromptUsage> findByStatusOrderByCreatedAtAsc(String queueStatus, Pageable pageable);

    Page<PromptUsage> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    @Query("SELECT pu FROM PromptUsage pu " +
            "WHERE pu.promptId = :promptId " +
            "AND pu.userId = :userId " +
            "ORDER BY pu.createdAt DESC")
    List<PromptUsage> findByPromptIdAndUserId(
            @Param("promptId") UUID promptId,
            @Param("userId") UUID userId
    );
}