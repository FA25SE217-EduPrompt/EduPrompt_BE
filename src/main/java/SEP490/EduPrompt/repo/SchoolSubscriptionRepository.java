package SEP490.EduPrompt.repo;

import SEP490.EduPrompt.model.SchoolSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface SchoolSubscriptionRepository extends JpaRepository<SchoolSubscription, UUID> {
    @Query("SELECT ss FROM SchoolSubscription ss WHERE ss.school.id = :schoolId AND ss.isActive = true")
    Optional<SchoolSubscription> findActiveBySchoolId(UUID schoolId);
}