package SEP490.EduPrompt.controller;

import SEP490.EduPrompt.dto.request.prompt.PromptTestRequest;
import SEP490.EduPrompt.dto.response.ResponseDto;
import SEP490.EduPrompt.dto.response.prompt.PromptTestResponse;
import SEP490.EduPrompt.service.ai.PromptTestingService;
import SEP490.EduPrompt.service.auth.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
@PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
public class PromptTestingController {
    private final PromptTestingService promptTestingService;

    /**
     * POST /api/prompts/test
     * Run prompt test action with idempotency support.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    public ResponseDto<PromptTestResponse> testPrompt(
            @RequestBody @Valid PromptTestRequest request,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        PromptTestResponse response = promptTestingService.testPrompt(currentUser.getUserId(), request, idempotencyKey);
        return ResponseDto.success(response);
    }

    /**
     * GET /api/prompts/test/prompt-usage/{usageId}
     * Get details for a specific test usage record.
     */
    @GetMapping("/prompt-usage/{usageId}")
    public ResponseDto<PromptTestResponse> getTestResult(@PathVariable UUID usageId) {
        PromptTestResponse response = promptTestingService.getTestResult(usageId);
        return ResponseDto.success(response);
    }

    @GetMapping("/{promptId}")
    public ResponseDto<List<PromptTestResponse>> getTestResults(@PathVariable UUID promptId) {
        List<PromptTestResponse> response = promptTestingService.getTestResultsByPromptId(promptId);
        return ResponseDto.success(response);
    }

    @GetMapping("/prompt-usage/my-history")
    public ResponseDto<Page<PromptTestResponse>> getUserTestResults(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<PromptTestResponse> response = promptTestingService.getUserTestHistory(currentUser.getUserId(), pageable);
        return ResponseDto.success(response);
    }
}

