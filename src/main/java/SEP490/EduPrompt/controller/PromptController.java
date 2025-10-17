package SEP490.EduPrompt.controller;

import SEP490.EduPrompt.dto.request.prompt.CreatePromptRequest;
import SEP490.EduPrompt.dto.response.ResponseDto;
import SEP490.EduPrompt.dto.response.prompt.PromptResponse;
import SEP490.EduPrompt.service.auth.UserPrincipal;
import SEP490.EduPrompt.service.prompt.PromptService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/prompts")
@RequiredArgsConstructor
@Slf4j
public class PromptController {
    private final PromptService promptService;

    @PostMapping("/standalone")
    @PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseDto<PromptResponse> createStandalonePrompt(
            @Valid @RequestBody CreatePromptRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        log.info("Creating standalone prompt by user: {}", currentUser.getUserId());
        PromptResponse response = promptService.createStandalonePrompt(request, currentUser);
        return ResponseDto.success(response);
    }

//    @PostMapping("/collection")
//    @PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
//    public ResponseDto<PromptResponse> createPromptInCollection(
//            @Valid @RequestBody CreatePromptRequest request,
//            @AuthenticationPrincipal UserPrincipal currentUser) {
//        log.info("Creating prompt in collection by user: {}", currentUser.getUserId());
//        PromptResponse response = promptService.createPromptInCollection(request, currentUser);
//        return ResponseDto.success(response);
//    }
}
