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
public class AdminServiceImpl implements AdminService {

    private final SchoolSubscriptionRepository schoolSubRepo;
    private final PermissionService permissionService;
    private final SchoolRepository schoolRepo;
    private final UserQuotaRepository userQuotaRepo;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepo;
    private final UserAuthRepository userAuthRepo;
    private final JwtUtil jwtUtil;

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
    public BulkAssignTeachersResponse bulkAssignTeachersToSchool(UUID adminUserId, BulkAssignTeachersRequest request) {
        User admin = userRepo.findById(adminUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));

        Set<String> uniqueEmails = validateInput(request, admin);
        UUID schoolId = admin.getSchoolId();
        SchoolSubscription activeSub = schoolSubRepo.findActiveBySchoolId(schoolId).orElse(null);

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
                    continue;
                }
                // Prevent cross-school reassignment
                if (user.getSchoolId() != null && !user.getSchoolId().equals(schoolId)) {
                    skipped.add(email + " (already in another school)");
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
            }

            user.setSchoolId(schoolId);
            usersToSave.add(user);
        }

        userRepo.saveAll(usersToSave);

        int assignedCount = usersToSave.size();
        List<UUID> listUserIds = usersToSave.stream().map(User::getId).toList();

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
        if (!permissionService.isAdmin(principal)) {
            throw new AccessDeniedException("Only SYSTEM_ADMIN can create a new school");
        }

        boolean exists = schoolRepo.existsByNameAndDistrictAndProvince(
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

        long tokenUsed = sub.getSchoolTokenPool() - sub.getSchoolTokenRemaining();

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
        teacher.setIsActive(false);

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

    //============Helper============
    private static Set<String> validateInput(BulkAssignTeachersRequest request, User admin) {
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

        Set<String> uniqueEmails = new HashSet<>(emails);
        return uniqueEmails;
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
}
