package SEP490.EduPrompt.repo;

import SEP490.EduPrompt.model.School;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SchoolRepository extends JpaRepository<School, UUID> {
    boolean existsByNameIgnoreCase(String name);

    Optional<School> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndDistrictIgnoreCaseAndProvinceIgnoreCase(String name, String district, String province);
}
