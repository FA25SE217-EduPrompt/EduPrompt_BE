package SEP490.EduPrompt.repo;

import SEP490.EduPrompt.model.Chapter;
import SEP490.EduPrompt.model.Lesson;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LessonRepository extends JpaRepository<Lesson, UUID> {
    List<Lesson> findByChapterIn(List<Chapter> chapters);
}