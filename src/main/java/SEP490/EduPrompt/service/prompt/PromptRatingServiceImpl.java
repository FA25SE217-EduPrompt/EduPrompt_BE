package SEP490.EduPrompt.service.prompt;

import SEP490.EduPrompt.dto.request.prompt.PromptRatingCreateRequest;
import SEP490.EduPrompt.dto.response.prompt.PromptRatingResponse;
import SEP490.EduPrompt.exception.auth.ResourceNotFoundException;
import SEP490.EduPrompt.model.Prompt;
import SEP490.EduPrompt.model.PromptRating;
import SEP490.EduPrompt.model.PromptViewLog;
import SEP490.EduPrompt.model.User;
import SEP490.EduPrompt.repo.PromptRatingRepository;
import SEP490.EduPrompt.repo.PromptRepository;
import SEP490.EduPrompt.repo.PromptViewLogRepository;
import SEP490.EduPrompt.repo.UserRepository;
import SEP490.EduPrompt.service.auth.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class PromptRatingServiceImpl implements PromptRatingService {

    private final PromptRepository promptRepository;
    private final UserRepository userRepository;
    private final PromptRatingRepository promptRatingRepository;
    private final PromptViewLogRepository promptViewLogRepository;

    @Override
    public PromptRatingResponse createPromptRating(PromptRatingCreateRequest request, UserPrincipal userPrincipal) {
        Prompt prompt = promptRepository.getReferenceById(request.promptId());
        if  (prompt == null) {
            throw new ResourceNotFoundException("Prompt not found");
        }
        User user = userRepository.getReferenceById(userPrincipal.getUserId());
        if (user == null) {
            throw new ResourceNotFoundException("User not found");
        }
        Optional<PromptViewLog> promptViewLog = promptViewLogRepository.findPromptViewLogByPromptAndUserId(prompt, userPrincipal.getUserId());
        PromptViewLog viewLog = null;
        if (promptViewLog.isPresent()) {
            viewLog = promptViewLog.get();
        }
        else {
            throw new ResourceNotFoundException("Prompt view log not found");
        }

        PromptRating rating = promptRatingRepository.findByPromptIdAndUserId(request.promptId(), userPrincipal.getUserId());
        if (rating == null) {
            rating = PromptRating.builder()
                    .user(user)
                    .prompt(prompt)
                    .rating(request.rating())
                    .build();
            promptRatingRepository.save(rating);
        }
        else {
            rating.setUser(user);
            rating.setPrompt(prompt);
            rating.setRating(request.rating());
            promptRatingRepository.save(rating);
        }
        return PromptRatingResponse.builder()
                .isDone(true)
                .build();
    }

    /**
     * Runs every day at 02:00 AM server time
     */
     @Scheduled(cron = "0 0 2 * * ?")   // 02:00 AM daily
    // @Scheduled(fixedRate = 5_000) // FOR TESTING: EVERY 5 SECOND
    @Transactional
    public void recalculateAllAverageRatings() {
        log.info("Starting daily average rating recalculation job...");

        var promptIds = promptRatingRepository.findAllDistinctPromptIds();

        int updated = 0;
        int cleared = 0;

        for (var promptId : promptIds) {
            var avg = promptRatingRepository.calculateAverageRatingByPromptId(promptId);

            // Round to 1 decimal place (e.g., 4.666 → 4.7)
            var roundedAvg = avg != null
                    ? Math.round(avg * 10.0) / 10.0
                    : null;

            updated += promptRepository.updateAvgRatingById(promptId, roundedAvg);
        }

        // Clean up prompts that have no ratings at all
        cleared = promptRepository.clearAvgRatingForUnratedPrompts();

        log.info("""
                Daily average rating job completed
                 • Prompts with updated avg_rating : {}
                 • Prompts cleared (no ratings)   : {}
                """, updated, cleared);
    }
}
