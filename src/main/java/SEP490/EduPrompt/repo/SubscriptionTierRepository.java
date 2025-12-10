package SEP490.EduPrompt.repo;

import SEP490.EduPrompt.model.SubscriptionTier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionTierRepository extends JpaRepository<SubscriptionTier, UUID> {
    Optional<SubscriptionTier> findByNameIgnoreCase(String tier);
}