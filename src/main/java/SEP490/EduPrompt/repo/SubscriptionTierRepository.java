package SEP490.EduPrompt.repo;

import SEP490.EduPrompt.model.SubscriptionTier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SubscriptionTierRepository extends JpaRepository<SubscriptionTier, UUID> {
    SubscriptionTier findByNameEndingWithIgnoreCase(String name);
}