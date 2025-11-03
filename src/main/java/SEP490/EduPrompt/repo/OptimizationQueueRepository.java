package SEP490.EduPrompt.repo;

import SEP490.EduPrompt.enums.QueueStatus;
import SEP490.EduPrompt.model.OptimizationQueue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OptimizationQueueRepository extends JpaRepository<OptimizationQueue, UUID> {

    List<OptimizationQueue> findByStatusOrderByCreatedAtAsc(String queueStatus);

    Optional<OptimizationQueue> findByIdempotencyKey(String idempotencyKey);

    @Query("SELECT COUNT(oq) FROM OptimizationQueue oq " +
            "WHERE oq.requestedBy = :userId " +
            "AND oq.status IN (:statuses) " +
            "AND oq.createdAt >= :since")
    int countByUserIdAndStatusInAndCreatedAtAfter(
            @Param("userId") UUID userId,
            @Param("statuses") List<QueueStatus> statuses,
            @Param("since") LocalDateTime since
    );

    List<OptimizationQueue> findByRequestedByIdOrderByCreatedAtDesc(UUID userId);

    @Query("SELECT oq FROM OptimizationQueue oq " +
            "WHERE oq.status = :status " +
            "AND oq.retryCount < oq.maxRetries " +
            "ORDER BY oq.createdAt ASC")
    List<OptimizationQueue> findPendingItemsForProcessing(@Param("status") QueueStatus status);
}