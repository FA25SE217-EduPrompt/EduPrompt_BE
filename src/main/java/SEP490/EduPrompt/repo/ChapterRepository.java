package SEP490.EduPrompt.repo;

import SEP490.EduPrompt.model.Chapter;
import SEP490.EduPrompt.model.Semester;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChapterRepository extends JpaRepository<Chapter, UUID> {
    List<Chapter> findBySemesterIn(List<Semester> semester);
}
