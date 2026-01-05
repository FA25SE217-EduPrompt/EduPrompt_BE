package SEP490.EduPrompt.repo;

import SEP490.EduPrompt.model.GradeLevel;
import SEP490.EduPrompt.model.Subject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface GradeLevelRepository extends JpaRepository<GradeLevel, UUID> {
    GradeLevel findByLevel(Integer level);
    GradeLevel findByLevelAndSubject(Integer level, Subject subject);
}