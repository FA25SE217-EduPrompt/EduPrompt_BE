package SEP490.EduPrompt.controller;

import SEP490.EduPrompt.dto.request.prompt.PromptOptimizationRequest;
import SEP490.EduPrompt.dto.response.ResponseDto;
import SEP490.EduPrompt.dto.response.prompt.OptimizationQueueResponse;
import SEP490.EduPrompt.service.ai.PromptOptimizationService;
import SEP490.EduPrompt.service.auth.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/prompts/optimize")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
public class PromptOptimizationController {

    private final PromptOptimizationService promptOptimizationService;

    /**
     * POST /api/prompts/optimize
     * Request prompt optimization. Returns immediately with queue ID.
     * Processing happens asynchronously in the background.
     * Supports idempotency to prevent duplicate requests.
     *
     * @param request Contains promptId, optimizationInput, temperature, maxTokens
     * @param idempotencyKey Optional UUID to prevent duplicate processing on retry
     * @param currentUser Authenticated user
     * @return OptimizationQueueResponse with queueId and status (PENDING)
     */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ResponseDto<OptimizationQueueResponse> requestOptimization(
            @RequestBody @Valid PromptOptimizationRequest request,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        log.info("POST /api/prompts/optimize - User: {}, Prompt: {}, IdempotencyKey: {}",
                currentUser.getUserId(), request.promptId(), idempotencyKey);

        // generate idempotency key if not provided
        String effectiveKey = idempotencyKey != null ? idempotencyKey : UUID.randomUUID().toString();

        OptimizationQueueResponse response = promptOptimizationService.requestOptimization(
                currentUser.getUserId(),
                request,
                effectiveKey
        );

        return ResponseDto.success(response);
    }

    /**
     * GET /api/prompts/optimize/queue/{queueId}
     * Get optimization status and result by queue ID.
     * Poll this endpoint to check if optimization is complete.
     *
     * Status flow: PENDING → PROCESSING → COMPLETED/FAILED
     *
     * @param queueId UUID of the optimization queue entry
     * @param currentUser Authenticated user (must be the requester)
     * @return OptimizationQueueResponse with current status and output (if completed)
     */
    @GetMapping("/queue/{queueId}")
    @ResponseStatus(HttpStatus.OK)
    public ResponseDto<OptimizationQueueResponse> getOptimizationStatus(
            @PathVariable UUID queueId,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        log.info("GET /api/prompts/optimize/queue/{} - User: {}", queueId, currentUser.getUserId());

        OptimizationQueueResponse response = promptOptimizationService.getOptimizationStatus(
                queueId,
                currentUser.getUserId()
        );

        return ResponseDto.success(response);
    }

    /**
     * GET /api/prompts/optimize/history
     * Get paginated optimization history for the current user.
     * Includes all statuses (PENDING, PROCESSING, COMPLETED, FAILED).
     *
     * @param page Page number (0-indexed)
     * @param size Page size (default 20)
     * @param currentUser Authenticated user
     * @return Paginated list of user's optimization history
     */
    @GetMapping("/history")
    @ResponseStatus(HttpStatus.OK)
    public ResponseDto<Page<OptimizationQueueResponse>> getUserOptimizationHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        log.info("GET /api/prompts/optimize/history - User: {}, Page: {}, Size: {}",
                currentUser.getUserId(), page, size);

        Pageable pageable = PageRequest.of(page, size);
        Page<OptimizationQueueResponse> response = promptOptimizationService.getUserOptimizationHistory(
                currentUser.getUserId(),
                pageable
        );

        return ResponseDto.success(response);
    }

    /**
     * GET /api/prompts/optimize/prompt/{promptId}/history
     * Get optimization history for a specific prompt by the current user.
     *
     * @param promptId UUID of the prompt
     * @param page Page number (0-indexed)
     * @param size Page size (default 20)
     * @param currentUser Authenticated user
     * @return Paginated list of optimization history for this prompt
     */
    @GetMapping("/prompt/{promptId}/history")
    @ResponseStatus(HttpStatus.OK)
    public ResponseDto<Page<OptimizationQueueResponse>> getPromptOptimizationHistory(
            @PathVariable UUID promptId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        log.info("GET /api/prompts/optimize/prompt/{}/history - User: {}, Page: {}, Size: {}",
                promptId, currentUser.getUserId(), page, size);

        Pageable pageable = PageRequest.of(page, size);
        Page<OptimizationQueueResponse> response = promptOptimizationService.getPromptOptimizationHistory(
                promptId,
                currentUser.getUserId(),
                pageable
        );

        return ResponseDto.success(response);
    }

    /**
     * GET /api/prompts/optimize/pending
     * Get list of pending optimization requests for the current user.
     * Useful for showing "in progress" optimizations in the UI.
     *
     * @param currentUser Authenticated user
     * @return List of pending/processing optimization requests
     */
    @GetMapping("/pending")
    @ResponseStatus(HttpStatus.OK)
    public ResponseDto<List<OptimizationQueueResponse>> getPendingOptimizations(
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        log.info("GET /api/prompts/optimize/pending - User: {}", currentUser.getUserId());

        List<OptimizationQueueResponse> response = promptOptimizationService.getPendingOptimizations(
                currentUser.getUserId()
        );

        return ResponseDto.success(response);
    }

    /**
     * POST /api/prompts/optimize/queue/{queueId}/retry
     * Retry a failed optimization request.
     * Only works for FAILED status optimizations.
     *
     * @param queueId UUID of the failed optimization queue entry
     * @param currentUser Authenticated user (must be the requester)
     * @return OptimizationQueueResponse with updated status (PENDING)
     */
    @PostMapping("/queue/{queueId}/retry")
    @ResponseStatus(HttpStatus.OK)
    public ResponseDto<OptimizationQueueResponse> retryOptimization(
            @PathVariable UUID queueId,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        log.info("POST /api/prompts/optimize/queue/{}/retry - User: {}", queueId, currentUser.getUserId());

        OptimizationQueueResponse response = promptOptimizationService.retryOptimization(
                queueId,
                currentUser.getUserId()
        );

        return ResponseDto.success(response);
    }

    /**
     * DELETE /api/prompts/optimize/queue/{queueId}
     * Cancel/delete an optimization request.
     * Can only cancel PENDING or FAILED requests.
     * Cannot cancel PROCESSING or COMPLETED requests.
     *
     * @param queueId UUID of the optimization queue entry
     * @param currentUser Authenticated user (must be the requester)
     * @return Success message
     */
    @DeleteMapping("/queue/{queueId}")
    @ResponseStatus(HttpStatus.OK)
    public ResponseDto<String> cancelOptimization(
            @PathVariable UUID queueId,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        log.info("DELETE /api/prompts/optimize/queue/{} - User: {}", queueId, currentUser.getUserId());

        promptOptimizationService.cancelOptimization(queueId, currentUser.getUserId());
        return ResponseDto.success("Optimization request cancelled successfully");
    }
}