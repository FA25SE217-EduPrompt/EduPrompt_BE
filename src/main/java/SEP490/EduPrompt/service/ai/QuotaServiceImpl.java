package SEP490.EduPrompt.service.ai;

import SEP490.EduPrompt.dto.response.quota.UserQuotaResponse;
import SEP490.EduPrompt.enums.QuotaType;
import SEP490.EduPrompt.exception.auth.InvalidInputException;
import SEP490.EduPrompt.exception.auth.ResourceNotFoundException;
import SEP490.EduPrompt.exception.client.QuotaExceededException;
import SEP490.EduPrompt.model.SchoolSubscription;
import SEP490.EduPrompt.model.SubscriptionTier;
import SEP490.EduPrompt.model.User;
import SEP490.EduPrompt.model.UserQuota;
import SEP490.EduPrompt.repo.SchoolSubscriptionRepository;
import SEP490.EduPrompt.repo.SubscriptionTierRepository;
import SEP490.EduPrompt.repo.UserQuotaRepository;
import SEP490.EduPrompt.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class QuotaServiceImpl implements QuotaService {

    private final UserQuotaRepository userQuotaRepository;
    private final UserRepository userRepository;
    private final SubscriptionTierRepository subscriptionTierRepository;
    private final SchoolSubscriptionRepository schoolSubscriptionRepository;

    @Override
    @Transactional(readOnly = true)
    public UserQuotaResponse getUserQuota(UUID userId) {
        log.info("Fetching quota for user: {}", userId);

        UserQuota userQuota = userQuotaRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Not found user quota"));

        return UserQuotaResponse.builder()
                .userId(userQuota.getUserId())
                .individualTokenLimit(userQuota.getIndividualTokenLimit())
                .individualTokenRemaining(userQuota.getIndividualTokenRemaining())
                .testingQuotaRemaining(userQuota.getTestingQuotaRemaining())
                .testingQuotaLimit(userQuota.getTestingQuotaLimit())
                .optimizationQuotaRemaining(userQuota.getOptimizationQuotaRemaining())
                .optimizationQuotaLimit(userQuota.getOptimizationQuotaLimit())
                .promptActionLimit(userQuota.getPromptActionLimit())
                .promptActionRemaining(userQuota.getCollectionActionRemaining())
                .collectionActionLimit(userQuota.getCollectionActionLimit())
                .collectionActionRemaining(userQuota.getCollectionActionRemaining())
                .promptUnlockLimit(userQuota.getPromptUnlockLimit())
                .promptUnlockRemaining(userQuota.getPromptUnlockRemaining())
                .quotaResetDate(userQuota.getQuotaResetDate())
                .build();
    }

    @Override
    public void validateQuota(UUID userId, QuotaType quotaType, int estimatedTokens) {
        UserQuota userQuota = userQuotaRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User quota not found"));

        String schoolSubId = userQuota.getSchoolSubscriptionId();
        if (schoolSubId != null) {
            SchoolSubscription schoolSub = schoolSubscriptionRepository.findById(UUID.fromString(schoolSubId))
                    .orElseThrow(() -> new ResourceNotFoundException("School subscription not found"));

            int tokenRemaining = schoolSub.getSchoolTokenRemaining();
            if (tokenRemaining < estimatedTokens) {
                throw new QuotaExceededException(
                        QuotaType.SCHOOL,
                        userQuota.getQuotaResetDate(),
                        tokenRemaining);
            }
        } else {
            int indvTokenRemaining = userQuota.getIndividualTokenRemaining();
            if (indvTokenRemaining < estimatedTokens) {
                throw new QuotaExceededException(QuotaType.INDIVIDUAL, userQuota.getQuotaResetDate(), indvTokenRemaining);
            }

            switch (quotaType) {
                case TEST -> {
                    int testQuota = userQuota.getTestingQuotaRemaining();
                    if (testQuota < 1)
                        throw new QuotaExceededException(QuotaType.TEST, userQuota.getQuotaResetDate(), testQuota);
                }

                case OPTIMIZATION -> {
                    int optQuota = userQuota.getOptimizationQuotaRemaining();
                    if (optQuota < 1)
                        throw new QuotaExceededException(QuotaType.OPTIMIZATION, userQuota.getQuotaResetDate(), optQuota);
                }

                default -> throw new InvalidInputException("Unknown quota type");
            }
        }
    }

    @Override
    @Transactional
    public void decrementQuota(UUID userId, QuotaType quotaType, int actualTokensUsed) {
        UserQuota userQuota = userQuotaRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User quota not found"));

        if (userQuota.getSchoolSubscriptionId() != null) {
            SchoolSubscription schoolSub = userQuota.getSchoolSubscription();
            int schoolTokensLeft = schoolSub.getSchoolTokenRemaining();
            if (schoolTokensLeft < actualTokensUsed) {
                throw new QuotaExceededException(QuotaType.SCHOOL, userQuota.getQuotaResetDate(), schoolTokensLeft);
            }

            schoolSub.setSchoolTokenRemaining(schoolTokensLeft - actualTokensUsed);
            schoolSub.setUpdatedAt(Instant.now());
        } else {
            int indvTokensLeft = userQuota.getIndividualTokenRemaining();
            if (indvTokensLeft < actualTokensUsed) {
                throw new QuotaExceededException(QuotaType.INDIVIDUAL, userQuota.getQuotaResetDate(), indvTokensLeft);
            }

            switch (quotaType) {
                case TEST -> {
                    int testQuota = userQuota.getTestingQuotaRemaining();
                    if (testQuota < 1)
                        throw new QuotaExceededException(QuotaType.TEST, userQuota.getQuotaResetDate(), testQuota);
                    userQuota.setTestingQuotaRemaining(testQuota - 1);
                }
                case OPTIMIZATION -> {
                    int optQuota = userQuota.getOptimizationQuotaRemaining();
                    if (optQuota < 1)
                        throw new QuotaExceededException(QuotaType.OPTIMIZATION, userQuota.getQuotaResetDate(), optQuota);
                    userQuota.setOptimizationQuotaRemaining(optQuota - 1);
                }
                default -> throw new InvalidInputException("Unknown quota type");
            }
            userQuota.setIndividualTokenRemaining(indvTokensLeft - actualTokensUsed);
        }
        userQuota.setUpdatedAt(Instant.now());
        userQuotaRepository.save(userQuota);
    }

    @Override
    @Transactional
    public void validateAndDecrementQuota(UUID userId, QuotaType quotaType, int tokenUsed) {
        log.info("Validating {} quota for user: {}", quotaType, userId);

        // prevent concurrent quota
        UserQuota userQuota = userQuotaRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new ResourceNotFoundException("user not found"));

        // Auto-reset if quota period expired
        if (Instant.now().isAfter(userQuota.getQuotaResetDate())) {
            log.info("Quota expired for user: {}, resetting...", userId);
            resetUserQuota(userQuota);
        }

        // if user have school sub, count token used toward school token pool, ignore test/optimize quota limit
        if (userQuota.getSchoolSubscriptionId() != null) {
            SchoolSubscription schoolSub = userQuota.getSchoolSubscription();

            int schoolTokensLeft = schoolSub.getSchoolTokenRemaining();
            if (schoolTokensLeft < tokenUsed) {
                throw new QuotaExceededException(QuotaType.SCHOOL, userQuota.getQuotaResetDate(), schoolTokensLeft);
            }
            schoolSub.setSchoolTokenRemaining(schoolTokensLeft - tokenUsed);
            schoolSub.setUpdatedAt(Instant.now());
            schoolSubscriptionRepository.save(schoolSub);
            log.info("Decremented school pool for user {} by {}, remaining: {}", userId, tokenUsed, schoolTokensLeft);
        }
        // if user dont have school sub, then proceed as normal
        else {
            int indvTokensLeft = userQuota.getIndividualTokenRemaining();
            if (indvTokensLeft < tokenUsed) {
                throw new QuotaExceededException(
                        QuotaType.INDIVIDUAL,
                        userQuota.getQuotaResetDate(),
                        userQuota.getIndividualTokenRemaining()
                );
            }

            switch (quotaType) {
                case TEST -> {
                    int testQuotaLeft = userQuota.getTestingQuotaRemaining();
                    if (testQuotaLeft < 1) {
                        throw new QuotaExceededException(
                                QuotaType.TEST,
                                userQuota.getQuotaResetDate(),
                                testQuotaLeft
                        );
                    }
                    userQuota.setTestingQuotaRemaining(testQuotaLeft - 1);
                }
                case OPTIMIZATION -> {
                    int optQuotaLeft = userQuota.getOptimizationQuotaRemaining();
                    if (optQuotaLeft < 1) {
                        throw new QuotaExceededException(
                                QuotaType.OPTIMIZATION,
                                userQuota.getQuotaResetDate(),
                                optQuotaLeft
                        );
                    }
                    userQuota.setOptimizationQuotaRemaining(optQuotaLeft - 1);
                }
                default -> throw new InvalidInputException("Unknown quota type");
            }

            userQuota.setIndividualTokenRemaining(indvTokensLeft - tokenUsed);
            log.info("Decremented individual quota pools for user {} by {} token(s); action: {}", userId, tokenUsed, quotaType);
        }

        userQuota.setUpdatedAt(Instant.now());
        userQuotaRepository.save(userQuota);
    }

    @Override
    @Scheduled(cron = "0 0 0 * * ?") // Run at midnight daily
    @Transactional
    public void resetExpiredQuotas() {
        log.info("Starting scheduled quota reset job");

        Instant now = Instant.now();
        List<UserQuota> expiredQuotas = userQuotaRepository.findByQuotaResetDateBefore(now);

        int resetCount = 0;
        for (UserQuota userQuota : expiredQuotas) {
            resetUserQuota(userQuota);
            resetCount++;
        }

        log.info("Quota reset job completed. Reset {} user quotas", resetCount);
    }

    @Override
    @Transactional
    public void syncUserQuotaWithSubscriptionTier(UUID userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id : " + userId));

        UUID subscriptionTierId = user.getSubscriptionTierId();

        log.info("Syncing quota for user: {} with subscription: {}", userId, subscriptionTierId);

        SubscriptionTier tier = subscriptionTierRepository.findById(subscriptionTierId)
                .orElse(getFreeTier().orElseThrow());


        UserQuota userQuota = userQuotaRepository.findByUserId(userId)
                .orElseGet(() -> UserQuota.builder()
                        .userId(userId)
                        .subscriptionTier(tier)
                        .createdAt(Instant.now())
                        .build());

        UserQuota updatedQuota = UserQuota.builder()
                .id(userQuota.getId())
                .user(user)
                .subscriptionTier(tier)
                .individualTokenLimit(tier.getIndividualTokenLimit())
                .individualTokenRemaining(tier.getIndividualTokenLimit())
                .testingQuotaLimit(tier.getTestingQuotaLimit())
                .testingQuotaRemaining(tier.getTestingQuotaLimit())
                .optimizationQuotaLimit(tier.getOptimizationQuotaLimit())
                .optimizationQuotaRemaining(tier.getOptimizationQuotaLimit())
                .collectionActionLimit(tier.getCollectionActionLimit())
                .collectionActionRemaining(tier.getCollectionActionLimit())
                .promptActionLimit(tier.getPromptActionLimit())
                .promptActionRemaining(tier.getPromptActionLimit())
                .promptUnlockLimit(tier.getPromptUnlockLimit())
                .promptUnlockRemaining(tier.getPromptUnlockLimit())
                .quotaResetDate(calculateNextResetDate())
                .createdAt(userQuota.getCreatedAt())
                .updatedAt(Instant.now())
                .build();

        userQuotaRepository.save(updatedQuota);
        log.info("Quota synced successfully for user: {}", userId);
    }

    @Override
    @Transactional
    public void syncUserQuotaWithSubscriptionTier(User user) {

        UUID subscriptionTierId = user.getSubscriptionTierId();
        UUID userId = user.getId();

        log.info("Sync quota for user: {} with subscription: {}", userId, subscriptionTierId);

        SubscriptionTier tier = subscriptionTierRepository.findById(subscriptionTierId)
                .orElse(getFreeTier().orElseThrow());


        UserQuota userQuota = userQuotaRepository.findByUserId(userId)
                .orElseGet(() -> UserQuota.builder()
                        .userId(userId)
                        .subscriptionTier(tier)
                        .createdAt(Instant.now())
                        .build());

        UserQuota updatedQuota = UserQuota.builder()
                .id(userQuota.getId())
                .user(user)
                .subscriptionTier(tier)
                .individualTokenLimit(tier.getIndividualTokenLimit())
                .individualTokenRemaining(tier.getIndividualTokenLimit())
                .testingQuotaLimit(tier.getTestingQuotaLimit())
                .testingQuotaRemaining(tier.getTestingQuotaLimit())
                .optimizationQuotaLimit(tier.getOptimizationQuotaLimit())
                .optimizationQuotaRemaining(tier.getOptimizationQuotaLimit())
                .collectionActionLimit(tier.getCollectionActionLimit())
                .collectionActionRemaining(tier.getCollectionActionLimit())
                .promptActionLimit(tier.getPromptActionLimit())
                .promptActionRemaining(tier.getPromptActionLimit())
                .promptUnlockRemaining(tier.getPromptUnlockLimit())
                .promptUnlockLimit(tier.getPromptUnlockLimit())
                .quotaResetDate(calculateNextResetDate())
                .createdAt(userQuota.getCreatedAt())
                .updatedAt(Instant.now())
                .build();

        userQuotaRepository.save(updatedQuota);
        log.info("Quota synced successfully for user: {}", userId);
    }

    private Optional<SubscriptionTier> getFreeTier() {
        return subscriptionTierRepository.findByNameIgnoreCase("free");
    }

    @Override
    @Transactional
    public void refundTokens(UUID userId, int tokensToRefund) {
        log.info("Refunding {} tokens for user: {}", tokensToRefund, userId);

        UserQuota userQuota = userQuotaRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User quota not found"));

        if (userQuota.getSchoolSubscriptionId() != null) {
            SchoolSubscription schoolSub = userQuota.getSchoolSubscription();
            schoolSub.setSchoolTokenRemaining(schoolSub.getSchoolTokenRemaining() + tokensToRefund);
            schoolSub.setUpdatedAt(Instant.now());
            schoolSubscriptionRepository.save(schoolSub);
        } else {
            // only refund token count
            int limit = userQuota.getIndividualTokenLimit();
            int current = userQuota.getIndividualTokenRemaining();
            userQuota.setIndividualTokenRemaining(Math.min(current + tokensToRefund, limit));
        }

        userQuota.setUpdatedAt(Instant.now());
        userQuotaRepository.save(userQuota);
    }

    @Override
    @Transactional
    public void refundQuota(UUID userId, QuotaType quotaType, int tokensToRefund) {
        // Refund tokens
        refundTokens(userId, tokensToRefund);

        // Also refund action count since operation failed
        UserQuota userQuota = userQuotaRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (userQuota.getSchoolSubscriptionId() == null) { // School users don't have action limits
            switch (quotaType) {
                case TEST -> {
                    int limit = userQuota.getTestingQuotaLimit();
                    int current = userQuota.getTestingQuotaRemaining();
                    userQuota.setTestingQuotaRemaining(Math.min(current + 1, limit));
                }
                case OPTIMIZATION -> {
                    int limit = userQuota.getOptimizationQuotaLimit();
                    int current = userQuota.getOptimizationQuotaRemaining();
                    userQuota.setOptimizationQuotaRemaining(Math.min(current + 1, limit));
                }
            }
            userQuota.setUpdatedAt(Instant.now());
            userQuotaRepository.save(userQuota);
        }
    }

    private void resetUserQuota(UserQuota userQuota) {
        log.debug("Resetting quota for user: {}", userQuota.getUserId());

        userQuota.setTestingQuotaRemaining(userQuota.getTestingQuotaLimit());
        userQuota.setOptimizationQuotaRemaining(userQuota.getOptimizationQuotaLimit());
        userQuota.setIndividualTokenRemaining(userQuota.getIndividualTokenLimit());
        userQuota.setPromptActionRemaining(userQuota.getPromptActionLimit());
        userQuota.setPromptUnlockRemaining(userQuota.getPromptUnlockLimit());
        userQuota.setCollectionActionRemaining(userQuota.getCollectionActionLimit());
        userQuota.setQuotaResetDate(calculateNextResetDate());
        userQuota.setUpdatedAt(Instant.now());

        userQuotaRepository.save(userQuota);
    }

    private Instant calculateNextResetDate() {
        return Instant.now()
                .plus(1, ChronoUnit.DAYS) // 1 day
                .truncatedTo(ChronoUnit.DAYS);
    }
}

