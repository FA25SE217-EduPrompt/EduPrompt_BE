package SEP490.EduPrompt.controller;

import SEP490.EduPrompt.dto.request.prompt.PromptRatingCreateRequest;
import SEP490.EduPrompt.dto.response.ResponseDto;
import SEP490.EduPrompt.dto.response.prompt.PromptRatingResponse;
import SEP490.EduPrompt.service.auth.UserPrincipal;
import SEP490.EduPrompt.service.prompt.PromptRatingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/prompts/ratings")
@RequiredArgsConstructor
@Slf4j
public class PromptRatingController {
    private final PromptRatingService service;

    @PostMapping
    public ResponseDto<PromptRatingResponse> ratePrompt(
            @Valid @RequestBody PromptRatingCreateRequest request,
            @AuthenticationPrincipal UserPrincipal auth) {

        log.info("Creating prompt rating by user {}", auth);
        var response = service.createPromptRating(request, auth);
        return ResponseDto.success(response);
    }
}
