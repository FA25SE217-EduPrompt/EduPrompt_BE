package SEP490.EduPrompt.repo;

import SEP490.EduPrompt.model.UserAuth;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public interface UserAuthRepository extends JpaRepository<UserAuth, UUID> {
    Optional<UserAuth> findByEmail(String email);

    Optional<UserAuth> findByUserId(UUID userId);

    boolean existsByEmail(String email);

    Optional<UserAuth> findByGoogleUserId(String googleUserId);

    @Query("SELECT ua FROM UserAuth ua WHERE ua.email IN :emails")
    List<UserAuth> findByEmailIn(List<String> emails);

    // Helper for Map<email, UserAuth>
    default Map<String, UserAuth> findMapByEmailIn(List<String> emails) {
        return findByEmailIn(emails).stream()
                .collect(Collectors.toMap(UserAuth::getEmail, ua -> ua));
    }
}
