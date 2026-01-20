package SEP490.EduPrompt.controller;

import SEP490.EduPrompt.dto.request.prompt.*;
import SEP490.EduPrompt.dto.response.ResponseDto;
import SEP490.EduPrompt.dto.response.prompt.*;
import SEP490.EduPrompt.service.auth.UserPrincipal;
import SEP490.EduPrompt.service.prompt.PromptService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/prompt-view-log")
@PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class PromptViewLogController {
    private final PromptService promptService;

    @PostMapping("/new")
    @Operation(summary = "this endpoint for creating a new prompt view log, if prompt view log has existed then only get not create")
    public ResponseDto<Boolean> logView(
            @Valid @RequestBody PromptViewLogCreateRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        boolean success = promptService.logPromptViews(principal, request);
        return ResponseDto.success(success);
    }

    @PostMapping("/prompt-view-log/has-viewed")
    @Operation(summary = "this endpoint for checking if the user has viewed these prompts before or not")
    public ResponseDto<List<ViewedPromptItem>> hasViewed(
            @Valid @RequestBody HasViewedPromptRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        List<ViewedPromptItem> results = promptService.hasUserViewedPrompts(principal, request);
        return ResponseDto.success(results);
    }
}
