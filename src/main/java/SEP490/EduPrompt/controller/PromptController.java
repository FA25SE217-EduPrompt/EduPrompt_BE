package SEP490.EduPrompt.controller;

import SEP490.EduPrompt.dto.request.prompt.*;
import SEP490.EduPrompt.dto.response.ResponseDto;
import SEP490.EduPrompt.dto.response.prompt.DetailPromptResponse;
import SEP490.EduPrompt.dto.response.prompt.PaginatedDetailPromptResponse;
import SEP490.EduPrompt.dto.response.prompt.PaginatedPromptResponse;
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
@RequestMapping("/api/prompts")
@RequiredArgsConstructor
@Slf4j
public class PromptController {
    private final PromptService promptService;

    @PostMapping("/standalone")
    @PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseDto<DetailPromptResponse> createStandalonePrompt(
            @Valid @RequestBody CreatePromptRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        log.info("Creating standalone prompt by user: {}", currentUser.getUserId());
        DetailPromptResponse response = promptService.createStandalonePrompt(request, currentUser);
        return ResponseDto.success(response);
    }

    @PostMapping("/collection")
    @PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
    @Operation(summary = "collection must NOT NULL")
    public ResponseDto<DetailPromptResponse> createPromptInCollection(
            @Valid @RequestBody CreatePromptCollectionRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        log.info("Creating prompt in collection by user: {}", currentUser.getUserId());
        DetailPromptResponse response = promptService.createPromptInCollection(request, currentUser);
        return ResponseDto.success(response);
    }

    //This function only get all private prompt of a specific user
    @GetMapping("/my-prompt")
    @PreAuthorize("hasAnyRole('TEACHER', 'SYSTEM_ADMIN')")
    public ResponseDto<PaginatedDetailPromptResponse> getMyPrompts(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("Retrieving private prompts for user: {}", currentUser.getUserId());
        Pageable pageable = PageRequest.of(page, size);
        return ResponseDto.success(promptService.getMyPrompts(currentUser, pageable));
    }

    //Get all prompt of a user - no condition on prompt
    @GetMapping("/user/{userId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseDto<PaginatedPromptResponse> getPromptsByUserId(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("Retrieving prompts by userId {} for user: {}", userId, currentUser.getUserId());
        Pageable pageable = PageRequest.of(page, size);
        return ResponseDto.success(promptService.getPromptsByUserId(currentUser, pageable, userId));
    }

    //Get all prompt of a user - no condition on prompt
    @GetMapping("/get-non-private")
    @PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseDto<PaginatedPromptResponse> getNonPrivatePrompt(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("Retrieving all non-private prompts ");
        Pageable pageable = PageRequest.of(page, size);
        return ResponseDto.success(promptService.getNonPrivatePrompts(currentUser, pageable));
    }

    //Get all prompt of a specific collection - no condition on prompt
    @GetMapping("/collection/{collectionId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseDto<PaginatedPromptResponse> getPromptsByCollectionId(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable UUID collectionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("Retrieving prompts by collectionId {} for user: {}", collectionId, currentUser.getUserId());
        Pageable pageable = PageRequest.of(page, size);
        return ResponseDto.success(promptService.getPromptsByCollectionId(currentUser, pageable, collectionId));
    }

    @PutMapping("/{promptId}/metadata")
    @PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseDto<DetailPromptResponse> updatePromptMetadata(
            @PathVariable UUID promptId,
            @Valid @RequestBody UpdatePromptMetadataRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        log.info("Updating metadata for prompt {} by user: {}", promptId, currentUser.getUserId());
        DetailPromptResponse response = promptService.updatePromptMetadata(promptId, request, currentUser);
        return ResponseDto.success(response);
    }

    @PutMapping("/{promptId}/visibility")
    @PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
    @Operation(summary = "collection id(NULLABLE) is for when the user want to change prompts that is PRIVATE/PUBLIC to GROUP/SCHOOL")
    public ResponseDto<DetailPromptResponse> updatePromptVisibility(
            @PathVariable UUID promptId,
            @Valid @RequestBody UpdatePromptVisibilityRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        log.info("Updating visibility for prompt {} by user: {}", promptId, currentUser.getUserId());
        DetailPromptResponse response = promptService.updatePromptVisibility(promptId, request, currentUser);
        return ResponseDto.success(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseDto<Void> softDeletePrompt(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        log.info("Soft-deleting prompt: {} by user: {}", id, currentUser.getUserId());
        promptService.softDeletePrompt(id, currentUser);
        return ResponseDto.success(null);
    }

    @GetMapping("/filter")
    @PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseDto<PaginatedPromptResponse> filterPrompts(
            @RequestParam(required = false) UUID createdBy,
            @RequestParam(required = false) String collectionName,
            @RequestParam(required = false) List<String> tagTypes,
            @RequestParam(required = false) List<String> tagValues,
            @RequestParam(required = false) String schoolName,
            @RequestParam(required = false) String groupName,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) Boolean includeDeleted,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        log.info("Filtering prompts for user: {} with params: createdBy={}, collectionName={}, tagTypes={}, tagValues={}, schoolName={}, groupName={}, title={}, includeDeleted={}",
                currentUser.getUserId(), createdBy, collectionName, tagTypes, tagValues, schoolName, groupName, title, includeDeleted);

        PromptFilterRequest request = new PromptFilterRequest(createdBy, collectionName, tagTypes, tagValues, schoolName, groupName, title, includeDeleted);
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        PaginatedPromptResponse response = promptService.filterPrompts(request, currentUser, pageable);
        return ResponseDto.success(response);
    }

    @GetMapping("/{promptId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
    @Operation(summary = "Get a prompt by its ID")
    public ResponseDto<DetailPromptResponse> getPromptById(
            @PathVariable UUID promptId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        log.info("Retrieving prompt with ID: {} for user: {}", promptId, currentUser.getUserId());
        DetailPromptResponse response = promptService.getPromptById(promptId, currentUser);
        return ResponseDto.success(response);
    }

}
