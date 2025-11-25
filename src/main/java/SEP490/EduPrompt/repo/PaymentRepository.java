package SEP490.EduPrompt.repo;

import SEP490.EduPrompt.model.Payment;
import SEP490.EduPrompt.model.Prompt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByTxnRef(String txnRef);

    Page<Payment> findByUserId(UUID userId, Pageable pageable);
}
