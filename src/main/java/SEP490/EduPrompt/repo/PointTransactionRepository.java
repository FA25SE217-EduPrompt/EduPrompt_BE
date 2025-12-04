package SEP490.EduPrompt.repo;

import SEP490.EduPrompt.model.PointTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface PointTransactionRepository extends JpaRepository<PointTransaction, UUID> {
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM PointTransaction p WHERE p.receiver.id = :userId AND p.status = 'completed'")
    Long sumIncomingByUserId(@Param("userId") UUID userId);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM PointTransaction p WHERE p.sender.id = :userId AND p.status = 'completed'")
    Long sumOutgoingByUserId(@Param("userId") UUID userId);
}
