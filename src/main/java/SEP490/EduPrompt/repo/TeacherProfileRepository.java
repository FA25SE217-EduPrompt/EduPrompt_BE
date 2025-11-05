package SEP490.EduPrompt.repo;

import SEP490.EduPrompt.model.TeacherProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface TeacherProfileRepository extends JpaRepository<TeacherProfile, UUID> {
    Optional<TeacherProfile> findByUserId(UUID userId);
    boolean existsByUserId(UUID userId);
}
