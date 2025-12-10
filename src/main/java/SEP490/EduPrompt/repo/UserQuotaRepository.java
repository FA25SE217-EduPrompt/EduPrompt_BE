package SEP490.EduPrompt.repo;

import SEP490.EduPrompt.model.UserQuota;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserQuotaRepository extends JpaRepository<UserQuota, UUID> {

    Optional<UserQuota> findByUserId(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT uq FROM UserQuota uq WHERE uq.userId = :userId")
    Optional<UserQuota> findByUserIdWithLock(@Param("userId") UUID userId);

    List<UserQuota> findByQuotaResetDateBefore(Instant date);

    @Query("SELECT uq FROM UserQuota uq WHERE uq.userId = :userId AND uq.subscriptionTierId = :subscriptionTierId")
    Optional<UserQuota> findByUserIdAndSubscriptionTierId(
            @Param("userId") UUID userId,
            @Param("subscriptionTierId") UUID subscriptionTierId
    );

    List<UserQuota> findAllByUserIdIn(Collection<UUID> userIds);
}
