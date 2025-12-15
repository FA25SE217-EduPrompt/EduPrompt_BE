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
import SEP490.EduPrompt.dto.response.teacherTokenUsed.PaginatedTeacherTokenUsageLogResponse;
import SEP490.EduPrompt.dto.response.teacherTokenUsed.SchoolUsageSummaryResponse;
import SEP490.EduPrompt.dto.response.teacherTokenUsed.TeacherTokenUsageLogResponse;
import SEP490.EduPrompt.dto.response.teacherTokenUsed.TeacherUsageResponse;
import SEP490.EduPrompt.enums.Role;
import SEP490.EduPrompt.exception.auth.AccessDeniedException;
import SEP490.EduPrompt.exception.auth.EmailAlreadyExistedException;
import SEP490.EduPrompt.exception.auth.InvalidInputException;
import SEP490.EduPrompt.exception.auth.ResourceNotFoundException;
import SEP490.EduPrompt.exception.generic.InvalidActionException;
import SEP490.EduPrompt.model.*;
import SEP490.EduPrompt.repo.*;
import SEP490.EduPrompt.service.ai.QuotaService;
import SEP490.EduPrompt.service.auth.UserPrincipal;
import SEP490.EduPrompt.service.permission.PermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminServiceImpl implements AdminService {

    private final SchoolSubscriptionRepository schoolSubRepo;
    private final PermissionService permissionService;
    private final SchoolRepository schoolRepo;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepo;
    private final UserAuthRepository userAuthRepo;
    private final SchoolEmailRepository schoolEmailRepo;
    private final TeacherTokenUsageLogRepository teacherTokenUsageLogRepo;
    private final SubscriptionTierRepository subscriptionTierRepo;
    private final UserQuotaRepository userQuotaRepository;

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

        log.info("Adding emails to school. SchoolId: {}, RequestedBy: {}, EmailCount: {}",
                schoolId, currentUser.getUserId(), request.emails().size());

        if (!permissionService.isSchoolAdmin(currentUser)) {
            log.warn("Access denied: User {} attempted to add emails without SCHOOL_ADMIN role", currentUser.getUserId());
            throw new AccessDeniedException("Only SCHOOL_ADMIN can add school emails");
        }

        School school = schoolRepo.findById(schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("School not found with id: " + schoolId));

        // Check school subscription early to avoid partial saves
        SchoolSubscription schoolSubscription = schoolSubRepo.findActiveBySchoolId(schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("Active school subscription not found for school id: " + schoolId));

        // Normalize emails
        Set<String> normalizedNewEmails = request.emails().stream()
                .map(String::trim)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        // Check for duplicates
        List<String> duplicates = normalizedNewEmails.stream()
                .filter(email -> schoolEmailRepo.existsBySchoolIdAndEmailIgnoreCase(schoolId, email))
                .toList();

        if (!duplicates.isEmpty()) {
            log.warn("Duplicate emails detected for school {}: {}", schoolId, duplicates);
            throw new InvalidActionException("Duplicate emails: " + String.join(", ", duplicates));
        }

        // Create and save new email entities
        List<SchoolEmail> newEmails = normalizedNewEmails.stream()
                .map(email -> SchoolEmail.builder()
                        .school(school)
                        .email(email)
                        .createdAt(Instant.now())
                        .build())
                .toList();

        schoolEmailRepo.saveAll(newEmails);
        log.info("Saved {} new school emails for school {}", newEmails.size(), schoolId);

        List<User> usersToUpdate = userRepo.findAllByEmailIn(normalizedNewEmails);

        if (!usersToUpdate.isEmpty()) {
            usersToUpdate.forEach(user -> user.setSchoolId(schoolId));

            // Batch fetch user quotas
            List<UUID> userIds = usersToUpdate.stream().map(User::getId).toList();
            List<UserQuota> quotasToUpdate = userQuotaRepository.findAllByUserIdIn(userIds);

            // Update school subscription for quotas
            quotasToUpdate.forEach(quota -> quota.setSchoolSubscription(schoolSubscription));

            userRepo.saveAll(usersToUpdate);
            userQuotaRepository.saveAll(quotasToUpdate);

            log.info("Updated {} users and {} quotas with school subscription for school {}",
                    usersToUpdate.size(), quotasToUpdate.size(), schoolId);
        }

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
                .subscriptionTier(subscriptionTierRepo.findByNameIgnoreCase("free").orElse(null))
                .firstName(registerRequest.getFirstName())
                .lastName(registerRequest.getLastName())
                .phoneNumber(registerRequest.getPhoneNumber())
                .email(registerRequest.getEmail().toLowerCase())
                .role(Role.SCHOOL_ADMIN.name())
                .isActive(true)
                .isVerified(true)
                .schoolId(registerRequest.getSchoolId())
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

        UserQuota userQuota = UserQuota.builder()
                .user(user)
                .build();
        setFreeTierQuota(userQuota);
        userQuotaRepository.save(userQuota);

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
                sub.getSchool().getName(),//need to be in transaction
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
    public Page<SchoolAdminTeacherResponse> getTeachersInSchool(UUID adminUserId, Pageable pageable) {
        User admin = permissionService.validateAndGetSchoolAdmin(adminUserId);
        UUID schoolId = admin.getSchoolId();

        return userRepo.findBySchoolIdAndRole(schoolId, Role.TEACHER.name(), pageable)
                .map(this::toTeacherResponse);
    }

    @Override
    @Transactional
    public void removeTeacherFromSchool(UUID adminUserId, RemoveTeacherFromSchoolRequest request) {
        User admin = permissionService.validateAndGetSchoolAdmin(adminUserId);
        UUID schoolId = admin.getSchoolId();
        UUID teacherId = request.teacherId();

        User teacher = userRepo.findById(teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher not found"));

        if (!Role.TEACHER.name().equalsIgnoreCase(teacher.getRole())) {
            throw new InvalidActionException("Can only remove teachers");
        }
        if (!schoolId.equals(teacher.getSchoolId())) {
            throw new AccessDeniedException("Teacher does not belong to your school");
        }

        // Remove from school
        teacher.setSchoolId(null);
        userRepo.save(teacher);

        UserQuota userQuota = userQuotaRepository.findByUserId(teacherId).orElseThrow(() -> new ResourceNotFoundException("user quota not found"));
        userQuota.setSchoolSubscription(null);
        userQuotaRepository.save(userQuota);
    }

    @Override
    public SchoolUsageSummaryResponse getSchoolTeachersUsage(UserPrincipal currentUser) {
        if(!permissionService.isSchoolAdmin(currentUser)) {
            throw new AccessDeniedException("You are not authorized to view this school");
        }
        User user = userRepo.findById(currentUser.getSchoolId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        School school = schoolRepo.findById(user.getSchoolId())
                .orElseThrow(() -> new ResourceNotFoundException("School not found"));
        SchoolSubscription activeSub = schoolSubRepo.findActiveBySchoolId(user.getSchoolId()).orElse(null);
        Integer schoolTokenPool = activeSub != null ? activeSub.getSchoolTokenPool() : null;
        Integer schoolTokenRemaining = activeSub != null ? activeSub.getSchoolTokenRemaining() : null;

        // School total used: compute in service (pool - remaining)
        Integer schoolTotalTokenUsed = (schoolTokenPool != null && schoolTokenRemaining != null) ? schoolTokenPool - schoolTokenRemaining : null;

        List<TeacherUsageResponse> teacherUsageLogList = List.of();
        List<User> teachers = new ArrayList<>();
        if (activeSub != null) {
            // Fetch all teachers
            teachers = userRepo.findBySchoolIdAndRole(user.getSchoolId(), Role.TEACHER.name());

            // Fetch all logs for the subscription (no aggregation in repo)
            List<TeacherTokenUsageLog> allLogs = teacherTokenUsageLogRepo.findBySchoolSubscriptionId(activeSub.getId());

            // Group and sum in service
            Map<UUID, Long> userTokenSums = allLogs.stream()
                    .collect(Collectors.groupingBy(
                            TeacherTokenUsageLog::getId,
                            Collectors.summingLong(TeacherTokenUsageLog::getTokensUsed)
                    ));

            // Build responses (0 if no logs for user)
            teacherUsageLogList = teachers.stream()
                    .map(teacher -> new TeacherUsageResponse(
                            teacher.getId(),
                            teacher.getFirstName(),
                            teacher.getLastName(),
                            teacher.getEmail(),
                            teacher.getPhoneNumber(),
                            teacher.getCreatedAt(),
                            userTokenSums.getOrDefault(teacher.getId(), 0L),
                            null
                    ))
                    .sorted(Comparator.comparingLong(TeacherUsageResponse::schoolTokensUsed).reversed()) // Optional: sort by usage desc
                    .toList();
        }
        return new SchoolUsageSummaryResponse(
                school.getName(),
                teachers.size(),
                schoolTokenPool,
                schoolTotalTokenUsed,
                schoolTokenRemaining,
                activeSub != null ? activeSub.getQuotaResetDate() : null,
                teacherUsageLogList
        );
    }

    @Override
    public PaginatedTeacherTokenUsageLogResponse getTokenUsageLogsBySchoolAndUser(
            UserPrincipal currentUser,
            UUID userId,
            Pageable pageable) {
        if (!permissionService.isSchoolAdmin(currentUser)) {
            throw new AccessDeniedException("You are not authorized to view this school");
        }
        User admin = userRepo.findById(currentUser.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        SchoolSubscription schoolSubscription =
                schoolSubRepo.findActiveBySchoolId(admin.getSchoolId()).orElse(null);

        if (schoolSubscription == null) {
            throw new ResourceNotFoundException("School subscription not found");
        }

        // You'll need to add this method to your repository
        Page<TeacherTokenUsageLog> logPage = teacherTokenUsageLogRepo
                .findBySchoolSubscriptionIdAndUserId(schoolSubscription.getId(), userId, pageable);

        List<TeacherTokenUsageLogResponse> content = logPage.getContent().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        return PaginatedTeacherTokenUsageLogResponse.builder()
                .content(content)
                .page(logPage.getNumber())
                .size(logPage.getSize())
                .totalElements(logPage.getTotalElements())
                .totalPages(logPage.getTotalPages())
                .build();
    }

    @Override
    public PaginatedTeacherTokenUsageLogResponse getTokenUsageLogsBySchool(UUID adminId, Pageable pageable) {
        User admin = permissionService.validateAndGetSchoolAdmin(adminId);
        UUID schoolId = admin.getSchoolId();
        SchoolSubscription schoolSubscription =
                schoolSubRepo.findActiveBySchoolId(schoolId).orElse(null);

        if (schoolSubscription == null) {
            throw new ResourceNotFoundException("School subscription not found");
        }
        // You'll need to add this method to your repository
        Page<TeacherTokenUsageLog> logPage =
                teacherTokenUsageLogRepo.findBySchoolSubscriptionId(schoolSubscription.getId(), pageable);

        List<TeacherTokenUsageLogResponse> content = logPage.getContent().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        return PaginatedTeacherTokenUsageLogResponse.builder()
                .content(content)
                .page(logPage.getNumber())
                .size(logPage.getSize())
                .totalElements(logPage.getTotalElements())
                .totalPages(logPage.getTotalPages())
                .build();
    }

    //Helper
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

    /**
     * Must be used in a Transaction
     * @param log
     * @return
     */
    private TeacherTokenUsageLogResponse mapToResponse(TeacherTokenUsageLog log) {
        return TeacherTokenUsageLogResponse.builder()
                .id(log.getId())
                .schoolSubscriptionId(log.getSchoolSubscriptionId())
                .subscriptionTierId(log.getSubscriptionTierId())
                .userId(log.getUserId())
                .tokensUsed(log.getTokensUsed())
                .usedAt(log.getUsedAt())
                .build();
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

    private void setFreeTierQuota(UserQuota userQuota) {
        Optional<SubscriptionTier> freeTier = subscriptionTierRepo.findByNameIgnoreCase("free");
        SubscriptionTier subscriptionTier;
        if (freeTier.isPresent()) {
            subscriptionTier = freeTier.get();
            userQuota.setSubscriptionTier(subscriptionTier);
            userQuota.setIndividualTokenLimit(subscriptionTier.getIndividualTokenLimit());
            userQuota.setIndividualTokenRemaining(subscriptionTier.getIndividualTokenLimit());
            userQuota.setTestingQuotaLimit(subscriptionTier.getTestingQuotaLimit());
            userQuota.setTestingQuotaRemaining(subscriptionTier.getTestingQuotaLimit());
            userQuota.setOptimizationQuotaLimit(subscriptionTier.getOptimizationQuotaLimit());
            userQuota.setOptimizationQuotaRemaining(subscriptionTier.getOptimizationQuotaLimit());
            userQuota.setPromptUnlockLimit(subscriptionTier.getPromptUnlockLimit());
            userQuota.setPromptUnlockRemaining(subscriptionTier.getPromptUnlockLimit());
            userQuota.setPromptActionLimit(subscriptionTier.getPromptActionLimit());
            userQuota.setPromptActionRemaining(subscriptionTier.getPromptActionLimit());
            userQuota.setCollectionActionLimit(subscriptionTier.getCollectionActionLimit());
            userQuota.setCollectionActionRemaining(subscriptionTier.getCollectionActionLimit());
            userQuota.setUpdatedAt(Instant.now());
        } else throw new ResourceNotFoundException("No free tier found");
    }
}
