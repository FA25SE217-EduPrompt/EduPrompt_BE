package SEP490.EduPrompt.service.admin;

import SEP490.EduPrompt.dto.request.RegisterRequest;
import SEP490.EduPrompt.dto.request.school.CreateSchoolRequest;
import SEP490.EduPrompt.dto.request.school.SchoolEmailRequest;
import SEP490.EduPrompt.dto.request.schoolAdmin.BulkAssignTeachersRequest;
import SEP490.EduPrompt.dto.request.schoolAdmin.RemoveTeacherFromSchoolRequest;
import SEP490.EduPrompt.dto.request.systemAdmin.CreateSchoolSubscriptionRequest;
import SEP490.EduPrompt.dto.response.RegisterResponse;
import SEP490.EduPrompt.dto.response.school.CreateSchoolResponse;
import SEP490.EduPrompt.dto.response.school.SchoolEmailResponse;
import SEP490.EduPrompt.dto.response.school.SchoolWithEmailsResponse;
import SEP490.EduPrompt.dto.response.schoolAdmin.SchoolAdminTeacherResponse;
import SEP490.EduPrompt.dto.response.schoolAdmin.SchoolSubscriptionUsageResponse;
import SEP490.EduPrompt.dto.response.systemAdmin.SchoolSubscriptionResponse;
import SEP490.EduPrompt.enums.Role;
import SEP490.EduPrompt.exception.auth.AccessDeniedException;
import SEP490.EduPrompt.exception.auth.EmailAlreadyExistedException;
import SEP490.EduPrompt.exception.auth.InvalidInputException;
import SEP490.EduPrompt.exception.auth.ResourceNotFoundException;
import SEP490.EduPrompt.exception.generic.InvalidActionException;
import SEP490.EduPrompt.model.*;
import SEP490.EduPrompt.repo.*;
import SEP490.EduPrompt.service.auth.UserPrincipal;
import SEP490.EduPrompt.service.permission.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final SchoolSubscriptionRepository schoolSubRepo;
    private final PermissionService permissionService;
    private final SchoolRepository schoolRepo;
    private final UserQuotaRepository userQuotaRepo;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepo;
    private final UserAuthRepository userAuthRepo;
    private final SchoolEmailRepository schoolEmailRepo;

    //============Helper============
    private static Set<String> validateRole(BulkAssignTeachersRequest request, User admin) {
        if (!Role.SCHOOL_ADMIN.name().equals(admin.getRole())) {
            throw new AccessDeniedException("Only SCHOOL_ADMIN can assign teachers");
        }
        if (admin.getSchoolId() == null) {
            throw new InvalidInputException("School admin has no school assigned");
        }

        List<String> emails = request.emails();
        if (emails.size() > 50) {
            throw new InvalidInputException("Maximum 50 emails allowed");
        }

        return new HashSet<>(emails);
    }

    @Override
    @Transactional
    public SchoolSubscriptionResponse createSchoolSubscription(UUID schoolId, CreateSchoolSubscriptionRequest request) {
        UserPrincipal principal = (UserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!permissionService.isSystemAdmin(principal)) {
            throw new AccessDeniedException("Only SYSTEM_ADMIN can create school subscriptions");
        }

        School school = schoolRepo.findById(schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("School not found: " + schoolId));

        // Optional: enforce one active per school
        schoolSubRepo.findActiveBySchoolId(schoolId).ifPresent(ss -> {
            throw new InvalidInputException("School already has an active subscription");
        });

        Instant now = Instant.now();
        SchoolSubscription sub = SchoolSubscription.builder()
                .school(school)
                .schoolTokenPool(request.schoolTokenPool())
                .schoolTokenRemaining(request.schoolTokenRemaining())
                .quotaResetDate(request.quotaResetDate())
                .startDate(request.startDate() != null ? request.startDate() : now)
                .endDate(request.endDate())
                .updatedAt(Instant.now())
                .isActive(true)
                .build();

        sub = schoolSubRepo.save(sub);

        return toSubscriptionResponse(sub);
    }

    @Override
    @Transactional
    public SchoolWithEmailsResponse addEmailsToSchool(UserPrincipal currentUser, SchoolEmailRequest request) {
        UUID schoolId = currentUser.getSchoolId();

        if (!permissionService.isSchoolAdmin(currentUser)) {
            throw new AccessDeniedException("Only SCHOOL_ADMIN can add school emails");
        }

        School school = schoolRepo.findById(schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("School not found with id: " + schoolId));

        // Normalize emails (trim + lowercase for duplicate check)
        Set<String> normalizedNewEmails = request.emails().stream()
                .map(String::trim)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        // Check for duplicates
        List<String> duplicates = normalizedNewEmails.stream()
                .filter(email -> schoolEmailRepo.existsBySchoolIdAndEmailIgnoreCase(schoolId, email))
                .toList();

        if (!duplicates.isEmpty()) {
            throw new InvalidActionException("Duplicate emails: " + String.join(", ", duplicates));
        }

        // Create new email entities
        List<SchoolEmail> newEmails = normalizedNewEmails.stream()
                .map(email -> SchoolEmail.builder()
                        .school(school)
                        .email(email)
                        .createdAt(Instant.now())
                        .build())
                .toList();

        schoolEmailRepo.saveAll(newEmails);

        // Refresh school with updated emails
        school.getSchoolEmails().addAll(newEmails);

        return mapToSchoolWithEmailsResponse(school);
    }

    @Override
    @Transactional
    public RegisterResponse createSchoolAdminAccount(RegisterRequest registerRequest) {
        UserPrincipal principal = (UserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!permissionService.isSystemAdmin(principal)) {
            throw new AccessDeniedException("Only SYSTEM_ADMIN can create school subscriptions");
        }
        if (userAuthRepo.existsByEmail(registerRequest.getEmail().toLowerCase())) {
            throw new EmailAlreadyExistedException();
        }

        Instant now = Instant.now();

        User user = User.builder()
                .firstName(registerRequest.getFirstName())
                .lastName(registerRequest.getLastName())
                .phoneNumber(registerRequest.getPhoneNumber())
                .email(registerRequest.getEmail().toLowerCase())
                .role(Role.SCHOOL_ADMIN.name())
                .isActive(true)
                .isVerified(true)
                .createdAt(now)
                .updatedAt(now)
                .build();

        User savedUser = userRepo.save(user);

        UserAuth userAuth = UserAuth.builder()
                .user(savedUser)
                .email(registerRequest.getEmail().toLowerCase())
                .passwordHash(passwordEncoder.encode(registerRequest.getPassword()))
                .verificationToken(null) //System admin will handle this case
                .createdAt(now)
                .updatedAt(now)
                .build();

        userAuthRepo.save(userAuth);

        return RegisterResponse.builder()
                .message("Create school admin successfully")
                .build();
    }

    @Override
    @Transactional
    public CreateSchoolResponse createSchool(CreateSchoolRequest request) {
        UserPrincipal principal = (UserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!permissionService.isSystemAdmin(principal)) {
            throw new AccessDeniedException("Only SYSTEM_ADMIN can create a new school");
        }

        boolean exists = schoolRepo.existsByNameIgnoreCaseAndDistrictIgnoreCaseAndProvinceIgnoreCase(
                request.name().trim(),
                request.district().trim(),
                request.province().trim()
        );
        if (exists) {
            throw new InvalidInputException("School already exist with name: " + request.name());
        }

        Instant now = Instant.now();

        School school = School.builder()
                .name(request.name().trim())
                .address(request.address() != null ? request.address().trim() : null)
                .phoneNumber(request.phoneNumber() != null ? request.phoneNumber().trim() : null)
                .district(request.district().trim())
                .province(request.province().trim())
                .createdAt(now)
                .updatedAt(now)
                .build();

        School savedSchool = schoolRepo.save(school);

        return CreateSchoolResponse.builder()
                .id(savedSchool.getId())
                .name(savedSchool.getName())
                .address(savedSchool.getAddress())
                .phoneNumber(savedSchool.getPhoneNumber())
                .district(savedSchool.getDistrict())
                .province(savedSchool.getProvince())
                .createdAt(savedSchool.getCreatedAt())
                .updatedAt(savedSchool.getUpdatedAt())
                .build();
    }

    @Override
    @Transactional
    public SchoolSubscriptionUsageResponse getSubscriptionUsage(UUID adminUserId) {
        User admin = permissionService.validateAndGetSchoolAdmin(adminUserId);
        UUID schoolId = admin.getSchoolId();

        SchoolSubscription sub = schoolSubRepo.findActiveBySchoolId(schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("No active subscription found"));

        long teacherCount = userRepo.countBySchoolIdAndRole(schoolId, Role.TEACHER.name());

        Integer tokenUsed = sub.getSchoolTokenPool() - sub.getSchoolTokenRemaining();

        return new SchoolSubscriptionUsageResponse(
                sub.getId(),
                sub.getSchool().getName(),
                sub.getSchoolTokenPool(),
                tokenUsed,
                sub.getSchoolTokenRemaining(),
                sub.getStartDate(),
                sub.getEndDate(),
                sub.getQuotaResetDate(),
                sub.getIsActive(),
                (int) teacherCount
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SchoolAdminTeacherResponse> getTeachersInSchool(UUID adminUserId, Pageable pageable) {
        User admin = permissionService.validateAndGetSchoolAdmin(adminUserId);
        UUID schoolId = admin.getSchoolId();

        return userRepo.findBySchoolIdAndRole(schoolId, Role.TEACHER.name(), pageable)
                .map(this::toTeacherResponse);
    }

    @Override
    public void removeTeacherFromSchool(UUID adminUserId, RemoveTeacherFromSchoolRequest request) {
        User admin = permissionService.validateAndGetSchoolAdmin(adminUserId);
        UUID schoolId = admin.getSchoolId();
        UUID teacherId = request.teacherId();

        User teacher = userRepo.findById(teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher not found"));

        if (!"TEACHER".equalsIgnoreCase(teacher.getRole())) {
            throw new InvalidActionException("Can only remove teachers");
        }
        if (!schoolId.equals(teacher.getSchoolId())) {
            throw new AccessDeniedException("Teacher does not belong to your school");
        }

        // Remove from school
        teacher.setSchoolId(null);

        // Reset quota to individual (zero)
        UserQuota quota = userQuotaRepo.findByUserId(teacherId)
                .orElseGet(() -> UserQuota.builder().user(teacher).build());

        quota.setSchoolSubscription(null);
        quota.setSubscriptionTier(null);
        quota.setIndividualTokenLimit(0);
        quota.setIndividualTokenRemaining(0);
        quota.setTestingQuotaLimit(0);
        quota.setOptimizationQuotaLimit(0);

        userRepo.save(teacher);
        userQuotaRepo.save(quota);
    }

    private SchoolSubscriptionResponse toSubscriptionResponse(SchoolSubscription sub) {
        return new SchoolSubscriptionResponse(
                sub.getId(),
                sub.getSchool().getId(),
                sub.getSchoolTokenPool(),
                sub.getSchoolTokenRemaining(),
                sub.getStartDate(),
                sub.getEndDate(),
                sub.getQuotaResetDate(),
                sub.getIsActive()
        );
    }

    private SchoolAdminTeacherResponse toTeacherResponse(User user) {
        return SchoolAdminTeacherResponse.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .isActive(user.getIsActive())
                .isVerified(user.getIsVerified())
                .createdAt(user.getCreatedAt())
                .build();
    }

    private boolean isValidEmail(String email) {
        String regex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
        return email != null && email.matches(regex);
    }

    private SchoolWithEmailsResponse mapToSchoolWithEmailsResponse(School school) {
        Set<SchoolEmailResponse> emailResponses = school.getSchoolEmails().stream()
                .map(email -> new SchoolEmailResponse(email.getId(), email.getEmail(), email.getCreatedAt()))
                .collect(Collectors.toSet());

        return new SchoolWithEmailsResponse(
                school.getId(),
                school.getName(),
                school.getAddress(),
                school.getPhoneNumber(),
                school.getDistrict(),
                school.getProvince(),
                school.getCreatedAt(),
                school.getUpdatedAt(),
                emailResponses
        );
    }
}
