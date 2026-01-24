package SEP490.EduPrompt.controller;

import SEP490.EduPrompt.dto.response.ResponseDto;
import SEP490.EduPrompt.dto.response.prompt.PromptResponse;
import SEP490.EduPrompt.model.Prompt;
import SEP490.EduPrompt.service.ai.PromptRecommendService;
import SEP490.EduPrompt.service.auth.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/prompts-recommend")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
public class PromptRecommendController {
    private final PromptRecommendService promptRecommendationService;

    @GetMapping("/recommended")
    public ResponseDto<List<PromptResponse>> getRecommendedPrompts(
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        List<PromptResponse> recommended = promptRecommendationService.getRecommendedPrompts(currentUser.getUserId());
        return ResponseDto.success(recommended);
    }
}
