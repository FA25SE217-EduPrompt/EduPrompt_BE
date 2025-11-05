package SEP490.EduPrompt.service.teacherProfile;


import SEP490.EduPrompt.dto.request.teacherProfile.CreateTeacherProfileRequest;
import SEP490.EduPrompt.dto.request.teacherProfile.UpdateTeacherProfileRequest;
import SEP490.EduPrompt.dto.response.teacherProfile.TeacherProfileResponse;
import SEP490.EduPrompt.exception.auth.AccessDeniedException;
import SEP490.EduPrompt.exception.auth.InvalidInputException;
import SEP490.EduPrompt.exception.auth.ResourceNotFoundException;
import SEP490.EduPrompt.model.TeacherProfile;
import SEP490.EduPrompt.model.User;
import SEP490.EduPrompt.repo.TeacherProfileRepository;
import SEP490.EduPrompt.repo.UserRepository;
import SEP490.EduPrompt.service.auth.UserPrincipal;
import SEP490.EduPrompt.service.permission.PermissionService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TeacherProfileServiceImpl implements TeacherProfileService {

    private final TeacherProfileRepository teacherProfileRepository;
    private final UserRepository userRepository;
    private final PermissionService permissionService;

    @Override
    @Transactional
    public TeacherProfileResponse createProfile(CreateTeacherProfileRequest request, UserPrincipal currentUser) {
        UUID currentUserId = currentUser.getUserId();
        log.info("Creating teacher profile for user: {}", currentUserId);

        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        permissionService.validateTeacherRole(user);

        if (teacherProfileRepository.existsByUserId(currentUserId)) {
            throw new InvalidInputException("Teacher profile already exists for this user");
        }

        TeacherProfile profile = TeacherProfile.builder()
                .user(user)
                .subjectSpecialty(trimOrNull(request.subjectSpecialty()))
                .gradeLevels(trimOrNull(request.gradeLevels()))
                .teachingStyle(trimOrNull(request.teachingStyle()))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        TeacherProfile saved = teacherProfileRepository.save(profile);
        log.info("Teacher profile created: {} for user: {}", saved.getId(), currentUserId);

        return TeacherProfileResponse.builder()
                .id(saved.getId())
                .subjectSpecialty(saved.getSubjectSpecialty())
                .gradeLevels(saved.getGradeLevels())
                .teachingStyle(saved.getTeachingStyle())
                .createdAt(saved.getCreatedAt())
                .updatedAt(saved.getUpdatedAt())
                .build();
    }

    @Override
    @Transactional
    public TeacherProfileResponse updateProfile(UpdateTeacherProfileRequest request, UserPrincipal currentUser) {
        UUID currentUserId = currentUser.getUserId();
        log.info("Updating teacher profile for user: {}", currentUserId);

        TeacherProfile profile = teacherProfileRepository.findByUserId(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher profile not found"));

        permissionService.validateOwnershipOrAdmin(profile, currentUser);

        if (request.subjectSpecialty() != null) {
            profile.setSubjectSpecialty(trimOrNull(request.subjectSpecialty()));
        }
        if (request.gradeLevels() != null) {
            profile.setGradeLevels(trimOrNull(request.gradeLevels()));
        }
        if (request.teachingStyle() != null) {
            profile.setTeachingStyle(trimOrNull(request.teachingStyle()));
        }

        profile.setUpdatedAt(Instant.now());
        TeacherProfile updated = teacherProfileRepository.save(profile);

        log.info("Teacher profile updated: {} by user: {}", updated.getId(), currentUserId);

        return TeacherProfileResponse.builder()
                .id(updated.getId())
                .subjectSpecialty(updated.getSubjectSpecialty())
                .gradeLevels(updated.getGradeLevels())
                .teachingStyle(updated.getTeachingStyle())
                .createdAt(updated.getCreatedAt())
                .updatedAt(updated.getUpdatedAt())
                .build();
    }

    @Override
    @Transactional
    public TeacherProfileResponse getMyProfile(UserPrincipal currentUser) {
        UUID currentUserId = currentUser.getUserId();
        log.info("Fetching teacher profile for user: {}", currentUserId);

        TeacherProfile profile = teacherProfileRepository.findByUserId(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher profile not found"));

        permissionService.validateOwnershipOrAdmin(profile, currentUser);

        return TeacherProfileResponse.builder()
                .id(profile.getId())
                .subjectSpecialty(profile.getSubjectSpecialty())
                .gradeLevels(profile.getGradeLevels())
                .teachingStyle(profile.getTeachingStyle())
                .createdAt(profile.getCreatedAt())
                .updatedAt(profile.getUpdatedAt())
                .build();
    }

    @Override
    @Transactional
    public TeacherProfileResponse getProfileByUserId(UUID userId, UserPrincipal currentUser) {
        if (!permissionService.isSystemAdmin(currentUser)) {
            throw new AccessDeniedException("Only SYSTEM_ADMIN can retrieve profile by user ID");
        }

        log.info("SYSTEM_ADMIN {} fetching profile for user: {}", currentUser.getUserId(), userId);

        TeacherProfile profile = teacherProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher profile not found for user: " + userId));

        return TeacherProfileResponse.builder()
                .id(profile.getId())
                .subjectSpecialty(profile.getSubjectSpecialty())
                .gradeLevels(profile.getGradeLevels())
                .teachingStyle(profile.getTeachingStyle())
                .createdAt(profile.getCreatedAt())
                .updatedAt(profile.getUpdatedAt())
                .build();
    }

    // Helper
    private String trimOrNull(String input) {
        return (input == null || input.isBlank()) ? null : input.trim();
    }
}
