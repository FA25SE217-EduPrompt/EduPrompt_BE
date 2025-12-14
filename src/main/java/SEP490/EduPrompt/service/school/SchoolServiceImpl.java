package SEP490.EduPrompt.service.school;

import SEP490.EduPrompt.dto.response.school.SchoolResponse;
import SEP490.EduPrompt.exception.auth.ResourceNotFoundException;
import SEP490.EduPrompt.model.SchoolEmail;
import SEP490.EduPrompt.model.User;
import SEP490.EduPrompt.repo.SchoolRepository;
import SEP490.EduPrompt.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SchoolServiceImpl implements SchoolService {

    private final SchoolRepository schoolRepository;
    private final UserRepository userRepository;

    @Override
    public Page<SchoolResponse> getAllSchools(Pageable pageable) {
        return schoolRepository.findAll(pageable)
                .map(school -> SchoolResponse.builder()
                        .id(school.getId())
                        .name(school.getName())
                        .address(school.getAddress())
                        .province(school.getProvince())
                        .district(school.getDistrict())
                        .phoneNumber(school.getPhoneNumber())
                        .schoolEmails(
                                school.getSchoolEmails()
                                        .stream()
                                        .map(SchoolEmail::getEmail)
                                        .collect(Collectors.toSet())
                        )
                        .createdAt(school.getCreatedAt())
                        .updatedAt(school.getUpdatedAt())
                        .build()
                );
    }

    @Override
    public SchoolResponse getSchoolById(UUID schoolId) {
        return schoolRepository.findById(schoolId).map(school -> SchoolResponse.builder()
                        .id(school.getId())
                        .name(school.getName())
                        .address(school.getAddress())
                        .province(school.getProvince())
                        .district(school.getDistrict())
                        .phoneNumber(school.getPhoneNumber())
                        .schoolEmails(
                                school.getSchoolEmails()
                                        .stream()
                                        .map(SchoolEmail::getEmail)
                                        .collect(Collectors.toSet())
                        )
                        .createdAt(school.getCreatedAt())
                        .updatedAt(school.getUpdatedAt())
                        .build())
                .orElseThrow(() -> new ResourceNotFoundException("School not found with id " + schoolId));
    }

    @Override
    public SchoolResponse getSchoolByUserId(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found with id " + userId));
        UUID schoolId = user.getSchoolId();
        if (schoolId == null) return null;
        return getSchoolById(schoolId);
    }
}
