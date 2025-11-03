package SEP490.EduPrompt.controller;

import SEP490.EduPrompt.dto.request.prompt.PromptOptimizationRequest;
import SEP490.EduPrompt.dto.response.ResponseDto;
import SEP490.EduPrompt.dto.response.prompt.OptimizationQueueResponse;
import SEP490.EduPrompt.service.ai.PromptOptimizationService;
import SEP490.EduPrompt.service.auth.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/prompts/optimize")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
public class PromptOptimizationController {
    private final PromptOptimizationService promptOptimizationService;

    /**
     * POST /api/prompts/optimize
     * Submit prompt for optimization action with idempotency.
     */
    @PostMapping
    public ResponseDto<OptimizationQueueResponse> optimizePrompt(
            @RequestBody @Valid PromptOptimizationRequest request,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        OptimizationQueueResponse response = promptOptimizationService.requestOptimization(
                currentUser.getUserId(),
                request,
                idempotencyKey
        );
        return ResponseDto.success(response);
    }

    /**
     * GET /api/prompts/optimize/{queueId}
     * Get optimization status or details.
     */
    @GetMapping("/{queueId}")
    public ResponseDto<OptimizationQueueResponse> getOptimizationStatus(
            @PathVariable UUID queueId,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        OptimizationQueueResponse response = promptOptimizationService.getOptimizationStatus(
                queueId,
                currentUser.getUserId()
        );
        return ResponseDto.success(response);
    }
}

