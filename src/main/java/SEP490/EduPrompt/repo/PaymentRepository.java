package SEP490.EduPrompt.repo;

import SEP490.EduPrompt.model.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Page<Payment> findByUserId(UUID userId, Pageable pageable);

    @Query(value = """
            SELECT
        EXTRACT(YEAR FROM created_at) AS year,
        EXTRACT(MONTH FROM created_at) AS month,
        COUNT(*) AS totalCount,
                SUM(CASE WHEN status = 'SUCCESS' THEN amount ELSE 0 END) AS totalPaid,
        SUM(CASE WHEN status = 'PENDING' THEN 1 ELSE 0 END) AS pendingCount,
                SUM(CASE WHEN status = 'SUCCESS' THEN 1 ELSE 0 END) AS successCount,
        SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) AS failedCount
    FROM payments
    GROUP BY EXTRACT(YEAR FROM created_at), EXTRACT(MONTH FROM created_at)
    ORDER BY year DESC, month DESC;
    """, nativeQuery = true)
    List<Object[]> getMonthlyPaymentSummaryRaw();

    // Or use derived queries / Specifications if preferred
    Page<Payment> findAll(Pageable pageable);

    Page<Payment> findByStatus(String status, Pageable pageable);

    Page<Payment> findByCreatedAtBetween(Instant start, Instant end, Pageable pageable);
}
