package SEP490.EduPrompt.repo;

import SEP490.EduPrompt.model.SchoolSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SchoolSubscriptionRepository extends JpaRepository<SchoolSubscription, UUID> {
}