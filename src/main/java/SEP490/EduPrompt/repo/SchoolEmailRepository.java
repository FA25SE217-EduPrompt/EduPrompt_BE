package SEP490.EduPrompt.repo;

import SEP490.EduPrompt.model.SchoolEmail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SchoolEmailRepository extends JpaRepository<SchoolEmail, UUID> {
    boolean existsBySchoolIdAndEmailIgnoreCase(UUID schoolId, String email);

    void deleteBySchoolIdAndEmailIn(UUID schoolId, List<String> emails);

    Optional<SchoolEmail> findByEmailIgnoreCase(String email);
}
