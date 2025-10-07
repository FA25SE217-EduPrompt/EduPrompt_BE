package SEP490.EduPrompt.repo;

import SEP490.EduPrompt.model.User;
import SEP490.EduPrompt.model.UserAuth;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserAuthRepository extends JpaRepository<UserAuth, UUID> {
    Optional<UserAuth> findByEmail(String email);

    Optional<UserAuth> findByUserId(UUID userId);

    boolean existsByEmail(String email);

    Optional<UserAuth> findByGoogleUserId(String googleUserId);
}
