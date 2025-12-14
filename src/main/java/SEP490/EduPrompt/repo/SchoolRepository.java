package SEP490.EduPrompt.repo;

import SEP490.EduPrompt.model.School;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SchoolRepository extends JpaRepository<School, UUID> {
    boolean existsByNameIgnoreCase(String name);

    Optional<School> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndDistrictIgnoreCaseAndProvinceIgnoreCase(String name, String district, String province);

    @NotNull
    Page<School> findAll(@NotNull Pageable pageable);

}
