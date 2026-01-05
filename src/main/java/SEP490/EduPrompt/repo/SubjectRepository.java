package SEP490.EduPrompt.repo;

import SEP490.EduPrompt.model.Subject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SubjectRepository extends JpaRepository<Subject, UUID> {
    List<Subject> findByNameContaining(String name);

    Subject findByNameIgnoreCase(String name);
}
