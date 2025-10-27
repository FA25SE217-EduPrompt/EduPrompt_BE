package SEP490.EduPrompt.service.ai;

import SEP490.EduPrompt.dto.response.quota.UserQuotaResponse;
import SEP490.EduPrompt.enums.QuotaType;
import SEP490.EduPrompt.exception.auth.ResourceNotFoundException;
import SEP490.EduPrompt.exception.client.QuotaExceededException;
import SEP490.EduPrompt.model.SubscriptionTier;
import SEP490.EduPrompt.model.User;
import SEP490.EduPrompt.model.UserQuota;
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
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class QuotaServiceImpl implements QuotaService {

    private final UserQuotaRepository userQuotaRepository;
    private final UserRepository userRepository;
    private final SubscriptionTierRepository subscriptionTierRepository;

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
                .quotaResetDate(userQuota.getQuotaResetDate())
                .build();
    }

    @Override
    @Transactional
    public void validateAndDecrementQuota(UUID userId, QuotaType quotaType) {
        log.info("Validating {} quota for user: {}", quotaType, userId);

        // prevent concurrent quota
        UserQuota userQuota = userQuotaRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new ResourceNotFoundException("user not found"));

        // Auto-reset if quota period expired
        if (Instant.now().isAfter(userQuota.getQuotaResetDate())) {
            log.info("Quota expired for user: {}, resetting...", userId);
            resetUserQuota(userQuota);
        }

        // Check and decrement quota
        switch (quotaType) {
            case TEST -> {
                if (userQuota.getTestingQuotaRemaining() <= 0) {
                    log.warn("Testing quota exceeded for user: {}", userId);
                    throw new QuotaExceededException(
                            QuotaType.TEST,
                            userQuota.getQuotaResetDate(),
                            userQuota.getTestingQuotaRemaining()
                    );
                }
                //TODO: check and decrement token, if user have school tier sub, then count toward to school token pool
                userQuota.setTestingQuotaRemaining(userQuota.getTestingQuotaRemaining() - 1);
                log.info("Testing quota decremented for user: {}. Remaining: {}",
                        userId, userQuota.getTestingQuotaRemaining());
            }
            case OPTIMIZATION -> {
                if (userQuota.getOptimizationQuotaRemaining() <= 0) {
                    log.warn("Optimization quota exceeded for user: {}", userId);
                    throw new QuotaExceededException(
                            QuotaType.OPTIMIZATION,
                            userQuota.getQuotaResetDate(),
                            userQuota.getOptimizationQuotaRemaining()
                    );
                }

                //TODO: the same as above , im broke
                userQuota.setOptimizationQuotaRemaining(userQuota.getOptimizationQuotaRemaining() - 1);
                log.info("Optimization quota decremented for user: {}. Remaining: {}",
                        userId, userQuota.getOptimizationQuotaRemaining());
            }
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
    public void syncUserQuotaWithSubscriptionTier(UUID userId, UUID subscriptionTierId) {
        log.info("Syncing quota for user: {} with subscription: {}", userId, subscriptionTierId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id : " + userId));

        SubscriptionTier tier = subscriptionTierRepository.findById(subscriptionTierId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription tier not found with id : " + subscriptionTierId));


        UserQuota userQuota = userQuotaRepository.findByUserId(userId)
                .orElseGet(() -> UserQuota.builder()
                        .userId(userId)
                        .subscriptionTier(tier)
                        .createdAt(Instant.now())
                        .build());

        UserQuota updatedQuota = UserQuota.builder()
                .id(userQuota.getId())
                .userId(userId)
                .subscriptionTier(tier)
                .individualTokenLimit(tier.getIndividualTokenLimit())
                .individualTokenRemaining(tier.getIndividualTokenLimit())
                .testingQuotaLimit(tier.getTestingQuotaLimit())
                .testingQuotaRemaining(tier.getTestingQuotaLimit())
                .optimizationQuotaLimit(tier.getOptimizationQuotaLimit())
                .optimizationQuotaRemaining(tier.getOptimizationQuotaLimit())
                .quotaResetDate(calculateNextResetDate())
                .createdAt(userQuota.getCreatedAt())
                .updatedAt(Instant.now())
                .build();

        userQuotaRepository.save(updatedQuota);
        log.info("Quota synced successfully for user: {}", userId);
    }

    private void resetUserQuota(UserQuota userQuota) {
        log.debug("Resetting quota for user: {}", userQuota.getUserId());

        userQuota.setTestingQuotaRemaining(userQuota.getTestingQuotaLimit());
        userQuota.setOptimizationQuotaRemaining(userQuota.getOptimizationQuotaLimit());
        userQuota.setIndividualTokenRemaining(userQuota.getIndividualTokenLimit());
        userQuota.setQuotaResetDate(calculateNextResetDate());
        userQuota.setUpdatedAt(Instant.now());

        userQuotaRepository.save(userQuota);
    }

    private Instant calculateNextResetDate() {
        return Instant.now()
                .plus(1, ChronoUnit.DAYS)
                .truncatedTo(ChronoUnit.DAYS);
    }
}

