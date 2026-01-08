package SEP490.EduPrompt.controller;

import SEP490.EduPrompt.dto.request.prompt.OptimizationRequest;
import SEP490.EduPrompt.dto.request.prompt.PromptScoringRequest;
import SEP490.EduPrompt.dto.response.ResponseDto;
import SEP490.EduPrompt.dto.response.prompt.OptimizationResponse;
import SEP490.EduPrompt.dto.response.prompt.PromptScoreResult;
import SEP490.EduPrompt.service.ai.PromptOptimizationService;
import SEP490.EduPrompt.service.prompt.PromptScoringService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v2/prompts")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
public class PromptOptimizationControllerV2 {

    private final PromptScoringService scoringService;
    private final PromptOptimizationService optimizationService;

    @PostMapping("/score")
    public ResponseDto<PromptScoreResult> scorePrompt(
            @RequestBody @Valid PromptScoringRequest request) {
        PromptScoreResult result = scoringService.scorePrompt(
                request.promptContent(),
                request.lessonId());
        return ResponseDto.success(result);
    }

    @PostMapping("/optimize")
    public ResponseDto<OptimizationResponse> optimizePrompt(
            @RequestBody @Valid OptimizationRequest request) {
        OptimizationResponse response = optimizationService.optimize(request);
        return ResponseDto.success(response);
    }

    @GetMapping("/optimization-result/{versionId}")
    @ResponseStatus(HttpStatus.OK)
    public ResponseDto<OptimizationResponse> getOptimizationResult(@PathVariable UUID versionId) {
        return ResponseDto.success(optimizationService.getOptimizationResult(versionId));
    }
}