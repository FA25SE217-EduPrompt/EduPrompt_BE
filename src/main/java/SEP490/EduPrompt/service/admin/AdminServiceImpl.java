package SEP490.EduPrompt.service.admin;

import SEP490.EduPrompt.dto.request.RegisterRequest;
import SEP490.EduPrompt.dto.request.school.CreateSchoolRequest;
import SEP490.EduPrompt.dto.request.schoolAdmin.BulkAssignTeachersRequest;
import SEP490.EduPrompt.dto.request.schoolAdmin.RemoveTeacherFromSchoolRequest;
import SEP490.EduPrompt.dto.request.systemAdmin.CreateSchoolSubscriptionRequest;
import SEP490.EduPrompt.dto.response.RegisterResponse;
import SEP490.EduPrompt.dto.response.school.CreateSchoolResponse;
import SEP490.EduPrompt.dto.response.schoolAdmin.BulkAssignTeachersResponse;
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
import SEP490.EduPrompt.util.JwtUtil;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminServiceImpl implements AdminService {

    private final SchoolSubscriptionRepository schoolSubRepo;
    private final PermissionService permissionService;
    private final SchoolRepository schoolRepo;
    private final SubscriptionTierRepository subRepo;
    private final UserQuotaRepository userQuotaRepo;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepo;
    private final UserAuthRepository userAuthRepo;
    private final JwtUtil jwtUtil;

    @Override
    @Transactional
    public SchoolSubscriptionResponse createSchoolSubscription(UUID schoolId, CreateSchoolSubscriptionRequest request) {
        log.info("Creating school subscription for schoolId: {}", schoolId);
        UserPrincipal principal = (UserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!permissionService.isSystemAdmin(principal)) {
            log.warn("Access denied: User {} attempted to create subscription without SYSTEM_ADMIN role", principal.getUserId());
            throw new AccessDeniedException("Only SYSTEM_ADMIN can create school subscriptions");

        }

        School school = schoolRepo.findById(schoolId)
                .orElseThrow(() -> {
                    log.error("School not found: {}", schoolId);
                    return new ResourceNotFoundException("School not found: " + schoolId);
                });

        // Optional: enforce one active per school
        schoolSubRepo.findActiveBySchoolId(schoolId).ifPresent(ss -> {
            log.warn("School {} already has an active subscription", schoolId);
            throw new InvalidInputException("School already has an active subscription");
        });

        Instant now = Instant.now();
        SchoolSubscription sub = SchoolSubscription.builder()
                .school(school)
                .schoolTokenPool(request.schoolTokenPool())
                .schoolTokenRemaining(request.schoolTokenRemaining())
                .quotaResetDate(request.quotaResetDate())
                .startDate(Instant.now())
                .endDate(request.endDate())
                .updatedAt(Instant.now())
                .isActive(true)
                .build();

        sub = schoolSubRepo.save(sub);
        log.info("Created school subscription {} for school {}", sub.getId(), schoolId);

        return toSubscriptionResponse(sub);
    }

    @Override
    @Transactional
    public BulkAssignTeachersResponse bulkAssignTeachersToSchool(UUID adminUserId, BulkAssignTeachersRequest request) {
        log.info("Bulk assigning teachers by adminUserId: {}", adminUserId);
        User admin = userRepo.findById(adminUserId)
                .orElseThrow(() -> {
                    log.error("Admin not found: {}", adminUserId);
                    return new ResourceNotFoundException("Admin not found");
                });

        Set<String> uniqueEmails = validateInput(request, admin);
        UUID schoolId = admin.getSchoolId();
        SchoolSubscription activeSub = schoolSubRepo.findActiveBySchoolId(schoolId).orElse(null);
        log.debug("Found active subscription for school {}: {}", schoolId, activeSub != null ? activeSub.getId() : "none");

        Map<String, UserAuth> existingMap = userAuthRepo.findMapByEmailIn(new ArrayList<>(uniqueEmails));

        List<User> usersToSave = new ArrayList<>();
        //This list is for email that not met the requirement for adding email to school
        List<String> skipped = new ArrayList<>();
        int createdCount = 0;

        for (String email : uniqueEmails) {
            User user;
            boolean isNew = false;

            if (existingMap.containsKey(email)) {
                UserAuth auth = existingMap.get(email);
                user = auth.getUser();

                // Validate role
                if (!Role.TEACHER.name().equalsIgnoreCase(user.getRole())) {
                    skipped.add(email + " (not a teacher)");
                    log.warn("Skipped {}: not a teacher (role: {})", email, user.getRole());
                    continue;
                }
                // Prevent cross-school reassignment
                if (user.getSchoolId() != null && !user.getSchoolId().equals(schoolId)) {
                    skipped.add(email + " (already in another school)");
                    log.warn("Skipped {}: already in school {}", email, user.getSchoolId());
                    continue;
                }

            } else {
                // Create new teacher
                user = User.builder()
                        .firstName("Pending")
                        .lastName("Teacher")
                        .email(email)
                        .role(Role.TEACHER.name())
                        .schoolId(schoolId)
                        .isActive(false)
                        .isVerified(false)
                        .build();
                userRepo.save(user);

                String token = jwtUtil.generateTokenWithExpiration(email, 1440);

                UserAuth auth = UserAuth.builder()
                        .user(user)
                        .email(email)
                        .verificationToken(token)
                        .build();

                userAuthRepo.save(auth); // cascade saves user
                isNew = true;
                createdCount++;
                log.info("Created new teacher for email: {}", email);
            }

            user.setSchoolId(schoolId);
            usersToSave.add(user);
        }

        if (!usersToSave.isEmpty()) {
            userRepo.saveAll(usersToSave);
            log.info("Saved {} assigned teachers for school {}", usersToSave.size(), schoolId);
        }

        int assignedCount = usersToSave.size();
        List<UUID> listUserIds = usersToSave.stream().map(User::getId).toList();
        log.info("Bulk assign completed: requested={}, assigned={}, created={}, skipped={}",
                uniqueEmails.size(), assignedCount, createdCount, skipped.size());

        return new BulkAssignTeachersResponse(
                listUserIds,
                uniqueEmails.size(),
                assignedCount,
                createdCount,
                skipped,
                List.of()  // why parsing an empty list ?
        );
    }

    @Override
    @Transactional
    public RegisterResponse createSchoolAdminAccount(RegisterRequest registerRequest) {
        log.info("Creating school admin account for email: {}", registerRequest.getEmail());
        UserPrincipal principal = (UserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!permissionService.isSystemAdmin(principal)) {
            log.warn("Access denied: User {} attempted to create school admin without SYSTEM_ADMIN role", principal.getUserId());
            throw new AccessDeniedException("Only SYSTEM_ADMIN can create school admin account");
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
        log.debug("Saved user: {}", savedUser.getId());

        UserAuth userAuth = UserAuth.builder()
                .user(savedUser)
                .email(registerRequest.getEmail().toLowerCase())
                .passwordHash(passwordEncoder.encode(registerRequest.getPassword()))
                .verificationToken(null) //System admin will handle this case
                .createdAt(now)
                .updatedAt(now)
                .build();

        userAuthRepo.save(userAuth);
        log.info("Created school admin auth for user: {}", savedUser.getId());

        return RegisterResponse.builder()
                .message("Create school admin successfully")
                .build();
    }

    @Override
    @Transactional
    public CreateSchoolResponse createSchool(CreateSchoolRequest request) {
        UserPrincipal principal = (UserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!permissionService.isAdmin(principal)) {
            log.warn("Access denied: User {} attempted to create school without admin permissions", principal.getUserId());
            throw new AccessDeniedException("Only SYSTEM_ADMIN can create a new school");
        }

        boolean exists = schoolRepo.existsByNameIgnoreCaseAndDistrictIgnoreCaseAndProvinceIgnoreCase(
                request.name().trim(),
                request.district().trim(),
                request.province().trim()
        );
        if (exists) {
            log.warn("School already exists: {} in {}/{}", request.name(), request.district(), request.province());
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
        log.info("Created school: {} (ID: {})", savedSchool.getName(), savedSchool.getId());

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
        log.info("Fetching subscription usage for adminUserId: {}", adminUserId);
        User admin = permissionService.validateAndGetSchoolAdmin(adminUserId);
        UUID schoolId = admin.getSchoolId();

        SchoolSubscription sub = schoolSubRepo.findActiveBySchoolId(schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("No active subscription found"));

        long teacherCount = userRepo.countBySchoolIdAndRole(schoolId, Role.TEACHER.name());

        int tokenUsed = sub.getSchoolTokenPool() - sub.getSchoolTokenRemaining();
        log.debug("Subscription usage: tokens used={}, remaining={}, teachers={}", tokenUsed, sub.getSchoolTokenRemaining(), teacherCount);

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
    @Transactional
    public Page<SchoolAdminTeacherResponse> getTeachersInSchool(UUID adminUserId, Pageable pageable) {
        log.info("Fetching teachers for adminUserId: {} (page: {})", adminUserId, pageable);
        User admin = permissionService.validateAndGetSchoolAdmin(adminUserId);
        UUID schoolId = admin.getSchoolId();

        Page<SchoolAdminTeacherResponse> teachers = userRepo.findBySchoolIdAndRole(schoolId, Role.TEACHER.name(), pageable)
                .map(this::toTeacherResponse);
        log.debug("Found {} teachers for school {}", teachers.getTotalElements(), schoolId);

        return teachers;
    }

    @Override
    @Transactional
    public void removeTeacherFromSchool(UUID adminUserId, RemoveTeacherFromSchoolRequest request) {
        log.info("Removing teacher {} by adminUserId: {}", request.teacherId(), adminUserId);
        User admin = permissionService.validateAndGetSchoolAdmin(adminUserId);
        SubscriptionTier tier = subRepo.findByNameEndingWithIgnoreCase("Free");
        UUID schoolId = admin.getSchoolId();
        UUID teacherId = request.teacherId();

        User teacher = userRepo.findById(teacherId)
                .orElseThrow(() -> {
                    log.error("Teacher not found: {}", teacherId);
                    return new ResourceNotFoundException("Teacher not found");
                });

        if (!Role.TEACHER.name().equalsIgnoreCase(teacher.getRole())) {
            log.warn("Invalid action: Attempt to remove non-teacher {} (role: {})", teacherId, teacher.getRole());
            throw new InvalidActionException("Can only remove teachers");
        }
        if (!schoolId.equals(teacher.getSchoolId())) {
            log.warn("Access denied: Teacher {} does not belong to school {}", teacherId, schoolId);
            throw new AccessDeniedException("Teacher does not belong to your school");
        }

        // Remove from school
        teacher.setSchoolId(null);
        teacher.setIsActive(false);

        // Reset quota to individual (zero)
        UserQuota quota = userQuotaRepo.findByUserId(teacherId)
                .orElseGet(() -> {
                    log.debug("Creating new quota for teacher {}", teacherId);
                    return UserQuota.builder().user(teacher).build();
                });

        //I don't think this is good, but it works. NEED REVIEW
        quota.setSchoolSubscription(null);
        quota.setSubscriptionTier(tier);
        quota.setOptimizationQuotaLimit(tier.getOptimizationQuotaLimit());
        quota.setOptimizationQuotaRemaining(tier.getOptimizationQuotaLimit());
        quota.setTestingQuotaRemaining(tier.getTestingQuotaLimit());
        quota.setTestingQuotaLimit(tier.getTestingQuotaLimit());
        quota.setQuotaResetDate(Instant.now());
        quota.setIndividualTokenRemaining(tier.getIndividualTokenLimit());
        quota.setIndividualTokenLimit(tier.getIndividualTokenLimit());
        quota.setCreatedAt(Instant.now());
        quota.setUpdatedAt(Instant.now());

        userRepo.save(teacher);
        userQuotaRepo.save(quota);
        log.info("Removed teacher {} from school {} and reset quota", teacherId, schoolId);
    }

    //============Helper============

    private Set<String> validateInput(BulkAssignTeachersRequest request, User admin) {
        log.debug("Validating input for bulk assign by admin: {}", admin.getId());
        if (!Role.SCHOOL_ADMIN.name().equals(admin.getRole())) {
            log.warn("Access denied: Non-school admin {} attempted bulk assign", admin.getId());
            throw new AccessDeniedException("Only SCHOOL_ADMIN can assign teachers");
        }
        if (admin.getSchoolId() == null) {
            log.warn("Invalid input: School admin {} has no school assigned", admin.getId());
            throw new InvalidInputException("School admin has no school assigned");
        }

        List<String> emails = request.emails();
        if (emails.size() > 50) {
            log.warn("Invalid input: Email list size {} exceeds max 50", emails.size());
            throw new InvalidInputException("Maximum 50 emails allowed");
        }

        Set<String> uniqueEmails = new HashSet<>(emails);
        log.debug("Unique emails after validation: {}", uniqueEmails.size());
        return uniqueEmails;
    }

    private SchoolSubscriptionResponse toSubscriptionResponse(SchoolSubscription sub) {
        log.debug("Mapping subscription {} to response", sub.getId());
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
        log.debug("Mapping teacher {} to response", user.getId());
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

}
