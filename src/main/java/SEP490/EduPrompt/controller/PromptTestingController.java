package SEP490.EduPrompt.controller;

import SEP490.EduPrompt.dto.request.prompt.PromptTestRequest;
import SEP490.EduPrompt.dto.response.ResponseDto;
import SEP490.EduPrompt.dto.response.prompt.PromptTestResponse;
import SEP490.EduPrompt.service.ai.PromptTestingService;
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
@RequestMapping("/api/prompts/test")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
public class PromptTestingController {

    private final PromptTestingService promptTestingService;

    /**
     * POST /api/prompts/test
     * Test a prompt with AI model and get results immediately.
     * Supports idempotency to prevent duplicate requests.
     *
     * @param request        Contains promptId, aiModel, inputText, temperature, maxTokens, topP
     * @param idempotencyKey Optional UUID to prevent duplicate processing on retry
     * @param currentUser    Authenticated user
     * @return PromptTestResponse with output, tokens used, execution time
     */
    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    public ResponseDto<PromptTestResponse> testPrompt(
            @RequestBody @Valid PromptTestRequest request,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        log.info("POST /api/prompts/test - User: {}, Prompt: {}, IdempotencyKey: {}",
                currentUser.getUserId(), request.promptId(), idempotencyKey);

        // generate idempotency key if not provided
        String effectiveKey = idempotencyKey != null ? idempotencyKey : String.valueOf(request.promptId());

        PromptTestResponse response = promptTestingService.testPrompt(
                currentUser.getUserId(),
                request,
                effectiveKey
        );

        return ResponseDto.success(response);
    }

    /**
     * GET /api/prompts/test/usage/{usageId}
     * Get details for a specific test usage record by ID.
     *
     * @param usageId UUID of the prompt usage record
     * @return PromptTestResponse with full test details
     */
    @GetMapping("/usage/{usageId}")
    @ResponseStatus(HttpStatus.OK)
    public ResponseDto<PromptTestResponse> getTestResult(
            @PathVariable UUID usageId
    ) {
        log.info("GET /api/prompts/test/usage/{}", usageId);

        PromptTestResponse response = promptTestingService.getTestResult(usageId);
        return ResponseDto.success(response);
    }

    /**
     * GET /api/prompts/test/prompt/{promptId}
     * Get all test results for a specific prompt.
     *
     * @param promptId UUID of the prompt
     * @return List of all test results for this prompt
     */
    @GetMapping("/prompt/{promptId}")
    @ResponseStatus(HttpStatus.OK)
    public ResponseDto<List<PromptTestResponse>> getTestResultsByPrompt(
            @PathVariable UUID promptId
    ) {
        log.info("GET /api/prompts/test/prompt/{}", promptId);

        List<PromptTestResponse> response = promptTestingService.getTestResultsByPromptId(promptId);
        return ResponseDto.success(response);
    }

    /**
     * GET /api/prompts/test/history
     * Get paginated test history for the current user.
     *
     * @param page        Page number (0-indexed)
     * @param size        Page size (default 20)
     * @param currentUser Authenticated user
     * @return Paginated list of user's test history
     */
    @GetMapping("/history")
    @ResponseStatus(HttpStatus.OK)
    public ResponseDto<Page<PromptTestResponse>> getUserTestHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        log.info("GET /api/prompts/test/history - User: {}, Page: {}, Size: {}",
                currentUser.getUserId(), page, size);

        Pageable pageable = PageRequest.of(page, size);
        Page<PromptTestResponse> response = promptTestingService.getUserTestHistory(
                currentUser.getUserId(),
                pageable
        );

        return ResponseDto.success(response);
    }

    /**
     * GET /api/prompts/test/prompt/{promptId}/history
     * Get paginated test history for a specific prompt by the current user.
     *
     * @param promptId    UUID of the prompt
     * @param page        Page number (0-indexed)
     * @param size        Page size (default 20)
     * @param currentUser Authenticated user
     * @return Paginated list of test history for this prompt
     */
    @GetMapping("/prompt/{promptId}/history")
    @ResponseStatus(HttpStatus.OK)
    public ResponseDto<Page<PromptTestResponse>> getPromptTestHistory(
            @PathVariable UUID promptId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        log.info("GET /api/prompts/test/prompt/{}/history - User: {}, Page: {}, Size: {}",
                promptId, currentUser.getUserId(), page, size);

        Pageable pageable = PageRequest.of(page, size);
        Page<PromptTestResponse> response = promptTestingService.getPromptTestHistory(
                promptId,
                currentUser.getUserId(),
                pageable
        );

        return ResponseDto.success(response);
    }

    /**
     * DELETE /api/prompts/test/usage/{usageId}
     * Delete a specific test usage record (soft delete).
     * Only the owner can delete their test results.
     *
     * @param usageId     UUID of the usage record to delete
     * @param currentUser Authenticated user
     * @return Success message
     */
    @DeleteMapping("/usage/{usageId}")
    @ResponseStatus(HttpStatus.OK)
    public ResponseDto<String> deleteTestResult(
            @PathVariable UUID usageId,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        log.info("DELETE /api/prompts/test/usage/{} - User: {}", usageId, currentUser.getUserId());

        promptTestingService.deleteTestResult(usageId, currentUser.getUserId());
        return ResponseDto.success("Test result deleted successfully");
    }
}