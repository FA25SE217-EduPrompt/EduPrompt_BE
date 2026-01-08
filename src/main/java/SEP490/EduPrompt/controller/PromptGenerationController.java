package SEP490.EduPrompt.controller;

import SEP490.EduPrompt.dto.request.prompt.GeneratePromptFromFileRequest;
import SEP490.EduPrompt.dto.response.ResponseDto;
import SEP490.EduPrompt.dto.response.prompt.GeneratePromptFromFileResponse;
import SEP490.EduPrompt.enums.PromptTask;
import SEP490.EduPrompt.service.ai.PromptGenerationService;
import SEP490.EduPrompt.service.auth.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/prompts")
@RequiredArgsConstructor
@Slf4j
public class PromptGenerationController {

    private final PromptGenerationService promptGenerationService;

    /**
     * Generate structured prompt from uploaded file
     * Form data:
     * - file: MultipartFile (required) - Reference document
     * - promptTask: PromptTask enum (required) - One of: LESSON_PLAN, SLIDE, TEST, TEST_MATRIX, GROUP_ACTIVITY
     * - customInstruction: String (optional) - Additional teacher requirements
     * <p>
     * Response: 5 structured prompt sections + metadata
     */
    @PostMapping(
            value = "/generate-from-file",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseDto<GeneratePromptFromFileResponse> generatePromptFromFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("promptTask") PromptTask promptTask,
            @RequestParam(value = "customInstruction", required = false) String customInstruction,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        log.info("Received prompt generation request for task: {}", promptTask);

        UUID userId = currentUser.getUserId();

        // Build request DTO
        GeneratePromptFromFileRequest request = new GeneratePromptFromFileRequest(
                promptTask,
                customInstruction
        );

        // Generate prompt
        GeneratePromptFromFileResponse response = promptGenerationService
                .generatePromptFromFile(userId, file, request);

        log.info("Prompt generation successful for user {}", userId);

        return ResponseDto.success(response);
    }

    /**
     * Get available prompt tasks
     */
    @GetMapping("/tasks")
    public ResponseDto<PromptTask[]> getAvailableTasks() {
        return ResponseDto.success(PromptTask.values());
    }
}
