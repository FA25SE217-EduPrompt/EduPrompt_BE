package SEP490.EduPrompt.controller;

import SEP490.EduPrompt.dto.request.prompt.CreatePromptCollectionRequest;
import SEP490.EduPrompt.dto.request.prompt.CreatePromptRequest;
import SEP490.EduPrompt.dto.request.prompt.UpdatePromptMetadataRequest;
import SEP490.EduPrompt.dto.request.prompt.UpdatePromptVisibilityRequest;
import SEP490.EduPrompt.dto.response.ResponseDto;
import SEP490.EduPrompt.dto.response.prompt.PaginatedPromptResponse;
import SEP490.EduPrompt.dto.response.prompt.PromptResponse;
import SEP490.EduPrompt.service.auth.UserPrincipal;
import SEP490.EduPrompt.service.prompt.PromptService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

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

    @PostMapping("/collection")
    @PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseDto<PromptResponse> createPromptInCollection(
            @Valid @RequestBody CreatePromptCollectionRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        log.info("Creating prompt in collection by user: {}", currentUser.getUserId());
        PromptResponse response = promptService.createPromptInCollection(request, currentUser);
        return ResponseDto.success(response);
    }

    //This function only get all private prompt of a specific user
    @GetMapping("/private")
    @PreAuthorize("hasAnyRole('TEACHER', 'SYSTEM_ADMIN')")
    public ResponseDto<PaginatedPromptResponse> getPrivatePrompts(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) UUID collectionId) {
        log.info("Retrieving private prompts for user: {}", currentUser.getUserId());
        Pageable pageable = PageRequest.of(page, size);
        return ResponseDto.success(promptService.getPrivatePrompts(currentUser, pageable, userId, collectionId));
    }

    //Get all prompt that belong to any school (School id was input when create)
    @GetMapping("/school")
    @PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseDto<PaginatedPromptResponse> getSchoolPrompts(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) UUID collectionId) {
        log.info("Retrieving school prompts for user: {}", currentUser.getUserId());
        Pageable pageable = PageRequest.of(page, size);
        return ResponseDto.success(promptService.getSchoolPrompts(currentUser, pageable, userId, collectionId));
    }

    // Get all prompt that exist in any specific group (Group id was input when create)
    @GetMapping("/group")
    @PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseDto<PaginatedPromptResponse> getGroupPrompts(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) UUID collectionId) {
        log.info("Retrieving group prompts for user: {}", currentUser.getUserId());
        Pageable pageable = PageRequest.of(page, size);
        return ResponseDto.success(promptService.getGroupPrompts(currentUser, pageable, userId, collectionId));
    }

    //This function only get all public prompt of a specific user
    @GetMapping("/public")
    @PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseDto<PaginatedPromptResponse> getPublicPrompts(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) UUID collectionId) {
        log.info("Retrieving public prompts for user: {}", currentUser.getUserId());
        Pageable pageable = PageRequest.of(page, size);
        return ResponseDto.success(promptService.getPublicPrompts(currentUser, pageable, userId, collectionId));
    }

    //This function currently use Paginated to get all prompt with create at ascending (will be changed to filter later)
    @GetMapping("/created-at")
    @PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseDto<PaginatedPromptResponse> getPromptsByCreatedAtAsc(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) UUID collectionId) {
        log.info("Retrieving prompts by createdAt for user: {}", currentUser.getUserId());
        Pageable pageable = PageRequest.of(page, size);
        return ResponseDto.success(promptService.getPromptsByCreatedAtAsc(currentUser, pageable, userId, collectionId));
    }

    //This function currently use Paginated to get all prompt with update at ascending (will be changed to filter later)
    @GetMapping("/updated-at")
    @PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseDto<PaginatedPromptResponse> getPromptsByUpdatedAtAsc(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) UUID collectionId) {
        log.info("Retrieving prompts by updatedAt for user: {}", currentUser.getUserId());
        Pageable pageable = PageRequest.of(page, size);
        return ResponseDto.success(promptService.getPromptsByUpdatedAtAsc(currentUser, pageable, userId, collectionId));
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
    public ResponseDto<PromptResponse> updatePromptMetadata(
            @PathVariable UUID promptId,
            @Valid @RequestBody UpdatePromptMetadataRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        log.info("Updating metadata for prompt {} by user: {}", promptId, currentUser.getUserId());
        PromptResponse response = promptService.updatePromptMetadata(promptId, request, currentUser);
        return ResponseDto.success(response);
    }

    @PutMapping("/{promptId}/visibility")
    @PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseDto<PromptResponse> updatePromptVisibility(
            @PathVariable UUID promptId,
            @Valid @RequestBody UpdatePromptVisibilityRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        log.info("Updating visibility for prompt {} by user: {}", promptId, currentUser.getUserId());
        PromptResponse response = promptService.updatePromptVisibility(promptId, request, currentUser);
        return ResponseDto.success(response);
    }
}
