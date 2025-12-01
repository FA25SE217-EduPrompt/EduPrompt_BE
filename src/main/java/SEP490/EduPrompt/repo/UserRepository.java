package SEP490.EduPrompt.repo;

import SEP490.EduPrompt.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("SELECT COUNT(u) FROM User u WHERE u.schoolId = :schoolId AND u.role = :role")
    long countBySchoolIdAndRole(UUID schoolId, String role);

    Page<User> findBySchoolIdAndRole(UUID schoolId, String role, Pageable pageable);

    Page<User> findAll(Pageable pageable);
}
