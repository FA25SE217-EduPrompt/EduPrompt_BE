package SEP490.EduPrompt.service.admin;

import SEP490.EduPrompt.dto.request.schoolAdmin.BulkAssignTeachersRequest;
import SEP490.EduPrompt.dto.request.systemAdmin.CreateSchoolSubscriptionRequest;
import SEP490.EduPrompt.dto.response.schoolAdmin.BulkAssignTeachersResponse;
import SEP490.EduPrompt.dto.response.systemAdmin.SchoolSubscriptionResponse;
import SEP490.EduPrompt.enums.Role;
import SEP490.EduPrompt.exception.auth.AccessDeniedException;
import SEP490.EduPrompt.exception.auth.InvalidInputException;
import SEP490.EduPrompt.exception.auth.ResourceNotFoundException;
import SEP490.EduPrompt.model.*;
import SEP490.EduPrompt.repo.*;
import SEP490.EduPrompt.service.auth.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final SchoolSubscriptionRepository schoolSubRepo;
    private final SubscriptionTierRepository tierRepo;
    private final SchoolRepository schoolRepo;
    private final UserRepository userRepo;
    private final UserAuthRepository userAuthRepo;
    private final UserQuotaRepository userQuotaRepo;

    @Override
    @Transactional
    public SchoolSubscriptionResponse createSchoolSubscription(UUID schoolId, CreateSchoolSubscriptionRequest request) {
        UserPrincipal principal = (UserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!Role.SYSTEM_ADMIN.name().equals(principal.getRole())) {
            throw new AccessDeniedException("Only SYSTEM_ADMIN can create school subscriptions");
        }

        School school = schoolRepo.findById(schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("School not found: " + schoolId));

        SubscriptionTier tier = tierRepo.findById(request.subscriptionTierId())
                .orElseThrow(() -> new ResourceNotFoundException("Subscription tier not found"));

        if (!tier.getIsActive()) {
            throw new InvalidInputException("Subscription tier is not active");
        }

        // Optional: enforce one active per school
        schoolSubRepo.findActiveBySchoolId(schoolId).ifPresent(ss -> {
            throw new InvalidInputException("School already has an active subscription");
        });

        Instant now = Instant.now();
        SchoolSubscription sub = SchoolSubscription.builder()
                .school(school)
                .schoolTokenPool(tier.getSchoolTokenPool())
                .schoolTokenRemaining(tier.getSchoolTokenPool())
                .quotaResetDate(now.plusSeconds(30L * 24 * 60 * 60)) // 30 days
                .startDate(request.startDate() != null ? request.startDate() : now)
                .endDate(request.endDate())
                .isActive(true)
                .build();

        sub = schoolSubRepo.save(sub);

        return toSubscriptionResponse(sub, tier.getName());
    }

    @Override
    @Transactional
    public BulkAssignTeachersResponse bulkAssignTeachersToSchool(UUID adminUserId, BulkAssignTeachersRequest request) {
        User admin = userRepo.findById(adminUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));

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
        UUID schoolId = admin.getSchoolId();
        SchoolSubscription activeSub = schoolSubRepo.findActiveBySchoolId(schoolId).orElse(null);

        Map<String, UserAuth> existingMap = userAuthRepo.findMapByEmailIn(new ArrayList<>(uniqueEmails));

        List<User> usersToSave = new ArrayList<>();
        List<UserQuota> quotasToSave = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        int createdCount = 0;

        for (String email : uniqueEmails) {
            User user;
            boolean isNew = false;

            if (existingMap.containsKey(email)) {
                UserAuth auth = existingMap.get(email);
                user = auth.getUser();

                // Validate role
                if (!"TEACHER".equalsIgnoreCase(user.getRole())) {
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
                        .role("TEACHER")
                        .schoolId(schoolId)
                        .isActive(true)
                        .isVerified(false)
                        .build();

                UserAuth auth = UserAuth.builder()
                        .user(user)
                        .email(email)
                        .verificationToken(UUID.randomUUID().toString())
                        .build();

                userAuthRepo.save(auth); // cascade saves user
                isNew = true;
                createdCount++;
            }

            user.setSchoolId(schoolId);
            user.setIsActive(true);
            usersToSave.add(user);

            // Apply school-wide quota
            UserQuota quota = userQuotaRepo.findByUserId(user.getId())
                    .orElseGet(() -> UserQuota.builder().user(user).build());

            if (activeSub != null) {
                quota.setSchoolSubscription(activeSub);
                quota.setSubscriptionTier(null);
                quota.setIndividualTokenLimit(0);
                quota.setIndividualTokenRemaining(0);
                quota.setTestingQuotaLimit(activeSub.getSchoolTokenPool());
                quota.setOptimizationQuotaLimit(activeSub.getSchoolTokenPool());
                quota.setQuotaResetDate(activeSub.getQuotaResetDate());
            } else {
                quota.setIndividualTokenLimit(0);
                quota.setIndividualTokenRemaining(0);
            }

            quotasToSave.add(quota);
        }

        userRepo.saveAll(usersToSave);
        userQuotaRepo.saveAll(quotasToSave);

        int assignedCount = usersToSave.size();

        return new BulkAssignTeachersResponse(
                uniqueEmails.size(),
                assignedCount,
                createdCount,
                skipped,
                List.of()
        );
    }


    //============Helper============
    private SchoolSubscriptionResponse toSubscriptionResponse(SchoolSubscription sub, String tierName) {
        return new SchoolSubscriptionResponse(
                sub.getId(),
                sub.getSchool().getId(),
                tierName,
                sub.getSchoolTokenPool(),
                sub.getSchoolTokenRemaining(),
                sub.getStartDate(),
                sub.getEndDate(),
                sub.getQuotaResetDate(),
                sub.getIsActive()
        );
    }
}
