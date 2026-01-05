package SEP490.EduPrompt.controller;

import SEP490.EduPrompt.dto.response.ResponseDto;
import SEP490.EduPrompt.dto.response.prompt.PromptShareResponse;
import SEP490.EduPrompt.service.auth.UserPrincipal;
import SEP490.EduPrompt.service.prompt.PromptService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/prompts-share")
@RequiredArgsConstructor
@Slf4j
public class PromptShareController {
    private final PromptService promptService;

    @PostMapping("/revoke-share/{promptId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
    @Operation(summary = "Revoke the share token for a prompt")
    public ResponseDto<Void> revokeShare(
            @PathVariable UUID promptId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        log.info("Revoking share for prompt {} by user: {}", promptId, currentUser.getUserId());
        promptService.revokeShare(promptId, currentUser);
        return ResponseDto.success(null);
    }

    @PostMapping("/{promptId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
    @Operation(summary = "Share a prompt and generate a share link")
    public ResponseDto<String> sharePrompt(
            @PathVariable UUID promptId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        log.info("Sharing prompt {} by user: {}", promptId, currentUser.getUserId());
        String shareLink = promptService.sharePrompt(promptId, currentUser);
        return ResponseDto.success(shareLink);
    }

    @GetMapping("/shared/{promptId}")
    @Operation(summary = "Get a shared prompt by ID and token (public access)")
    public ResponseDto<PromptShareResponse> getSharedPrompt(
            @PathVariable UUID promptId,
            @RequestParam UUID token) {
        log.info("Retrieving shared prompt with ID: {}", promptId);
        PromptShareResponse response = promptService.getSharedPrompt(promptId, token);
        return ResponseDto.success(response);
    }
}
