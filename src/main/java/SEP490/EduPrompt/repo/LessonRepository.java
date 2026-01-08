package SEP490.EduPrompt.repo;

import SEP490.EduPrompt.model.Chapter;
import SEP490.EduPrompt.model.Lesson;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LessonRepository extends JpaRepository<Lesson, UUID> {
    List<Lesson> findByChapterIn(List<Chapter> chapters);

    @Query("""
                SELECT l FROM Lesson l
                JOIN FETCH l.chapter c
                JOIN FETCH c.semester s
                JOIN FETCH s.gradeLevel g
                JOIN FETCH g.subject sub
                WHERE l.id = :id
            """)
    Optional<Lesson> findByIdWithHierarchy(@Param("id") UUID id);

    @Query(value = """
                SELECT 
                    l.id as lessonId,
                    l.name as lessonName,
                    l.content as lessonContent,
                    c.name as chapterName,
                    sub.name as subjectName,
                    g.level as gradeLevel,
                    ts_rank(
                        to_tsvector('simple', l.name || ' ' || COALESCE(l.content, '')),
                        plainto_tsquery('simple', :keyword)
                    ) as relevanceScore
                FROM lessons l
                JOIN chapters c ON l.chapter_id = c.id
                JOIN semesters sem ON c.semester_id = sem.id
                JOIN grade_levels g ON sem.grade_level_id = g.id
                JOIN subjects sub ON g.subject_id = sub.id
                WHERE 
                    to_tsvector('simple', l.name || ' ' || COALESCE(l.content, '')) @@ plainto_tsquery('simple', :keyword)
                    AND (:subjectId IS NULL OR sub.id = :subjectId)
                    AND (:gradeLevel IS NULL OR g.level = :gradeLevel)
                ORDER BY relevanceScore DESC
                LIMIT 10
            """, nativeQuery = true)
    List<LessonSearchResultProjection> searchByKeyword(
            @Param("keyword") String keyword,
            @Param("subjectId") UUID subjectId,
            @Param("gradeLevel") Integer gradeLevel
    );

    interface LessonSearchResultProjection {
        UUID getLessonId();

        String getLessonName();

        String getLessonContent();

        String getChapterName();

        String getSubjectName();

        Integer getGradeLevel();

        Double getRelevanceScore();
    }
}
