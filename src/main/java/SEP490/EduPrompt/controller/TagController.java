package SEP490.EduPrompt.controller;

import SEP490.EduPrompt.dto.request.tag.*;
import SEP490.EduPrompt.dto.response.ResponseDto;
import SEP490.EduPrompt.service.auth.UserPrincipal;
import SEP490.EduPrompt.service.tag.CollectionTagService;
import SEP490.EduPrompt.service.tag.PromptTagService;
import SEP490.EduPrompt.service.tag.TagService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
@Slf4j
public class TagController {
    private final TagService tagService;
    private final PromptTagService promptTagService;
    private final CollectionTagService collectionTagService;

    @PostMapping("/batch")
    @PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
    @Operation(summary = "Create tag, can create one or multiple tags")
    public ResponseDto<?> createTags(
            @Valid @RequestBody CreateTagBatchRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        log.info("Creating tags batch by user: {}", currentUser.getUserId());
        return ResponseDto.success(tagService.createBatch(request));
    }

    @PostMapping("/prompts/{promptId}/batch")
    @PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
    @Operation(summary = "Add one or multiple tag to a prompt")
    public ResponseDto<?> addTagsToPrompt(
            @PathVariable UUID promptId,
            @Valid @RequestBody AddTagsToPromptRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        log.info("Adding tags to prompt {} by user: {}", promptId, currentUser.getUserId());
        return ResponseDto.success(promptTagService.addTags(promptId, request, currentUser));
    }

    @PostMapping("/collections/{collectionId}/batch")
    @PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
    @Operation(summary = "Add one or multiple tag to a collection")
    public ResponseDto<?> addTagsToCollection(
            @PathVariable UUID collectionId,
            @Valid @RequestBody AddTagsToCollectionRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        log.info("Adding tags to collection {} by user: {}", collectionId, currentUser.getUserId());
        return ResponseDto.success(collectionTagService.addTags(collectionId, request, currentUser));
    }

    @DeleteMapping("/prompts/{promptId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
    @Operation(summary = "Remove one or multiple tags from a prompt")
    public ResponseDto<?> removeTagFromPrompt(
            @PathVariable UUID promptId,
            @Valid @RequestBody RemoveTagFromPromptRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        log.info("Removing tag from prompt {} by user: {}", promptId, currentUser.getUserId());
        promptTagService.removeTag(promptId, request, currentUser);
        return ResponseDto.success("Tag removed from prompt successfully");
    }

    @DeleteMapping("/collections/{collectionId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
    @Operation(summary = "Remove one or multiple tags from a collection")
    public ResponseDto<?> removeTagFromCollection(
            @PathVariable UUID collectionId,
            @Valid @RequestBody RemoveTagFromCollectionRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        log.info("Removing tag from collection {} by user: {}", collectionId, currentUser.getUserId());
        collectionTagService.removeTag(collectionId, request, currentUser);
        return ResponseDto.success("Tag removed from collection successfully");
    }

    @GetMapping("/prompts/{promptId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
    @Operation(summary = "Get all tag of a prompt")
    public ResponseDto<?> getTagsForPrompt(
            @PathVariable UUID promptId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        log.info("Retrieving tags for prompt {} by user: {}", promptId, currentUser.getUserId());
        return ResponseDto.success(promptTagService.getTagsForPrompt(promptId, currentUser));
    }

    @GetMapping("/collections/{collectionId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
    @Operation(summary = "Get all tags of a collection")
    public ResponseDto<?> getTagsForCollection(
            @PathVariable UUID collectionId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        log.info("Retrieving tags for collection {} by user: {}", collectionId, currentUser.getUserId());
        return ResponseDto.success(collectionTagService.getTagsForCollection(collectionId, currentUser));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
    @Operation(summary = "Get all tag, can be filtered by type")
    public ResponseDto<?> getTags(
            @RequestParam(required = false) List<String> type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        Pageable pageable = PageRequest.of(page, size);
        log.info("Retrieving tags (types: {}, page: {}, size: {}) by user: {}", type, page, size, currentUser.getUserId());
        var result = tagService.filterTags(type, pageable);
        return ResponseDto.success(result);
    }
}
