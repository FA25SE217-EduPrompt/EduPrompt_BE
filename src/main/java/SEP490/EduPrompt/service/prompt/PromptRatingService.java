package SEP490.EduPrompt.service.prompt;

import SEP490.EduPrompt.dto.request.prompt.PromptRatingCreateRequest;
import SEP490.EduPrompt.dto.response.prompt.PromptRatingResponse;
import SEP490.EduPrompt.service.auth.UserPrincipal;

import java.util.UUID;

public interface PromptRatingService {

    PromptRatingResponse createPromptRating(PromptRatingCreateRequest request, UserPrincipal userPrincipal);
}
