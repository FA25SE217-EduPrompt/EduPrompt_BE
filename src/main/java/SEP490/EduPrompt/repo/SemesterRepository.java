package SEP490.EduPrompt.repo;

import SEP490.EduPrompt.model.GradeLevel;
import SEP490.EduPrompt.model.Semester;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SemesterRepository extends JpaRepository<Semester, UUID> {
    @Query("select s from Semester s where s.gradeLevel = :gradeLevel and s.semesterNumber = :semesterNumber")
    Semester findByGradeLevelAndSemesterNumber(GradeLevel gradeLevel, Integer semesterNumber);

    @Query("select s from Semester s where s.gradeLevel = :gradeLevel")
    List<Semester> findByGradeLevel(GradeLevel gradeLevel);
}
