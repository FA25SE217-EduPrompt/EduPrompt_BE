package SEP490.EduPrompt.controller;

import SEP490.EduPrompt.dto.request.collection.CreateCollectionRequest;
import SEP490.EduPrompt.dto.request.collection.UpdateCollectionRequest;
import SEP490.EduPrompt.dto.response.ResponseDto;
import SEP490.EduPrompt.service.auth.UserPrincipal;
import SEP490.EduPrompt.service.collection.CollectionService;
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
@RequestMapping("/api/collections")
@RequiredArgsConstructor
@Slf4j
public class CollectionController {

    private final CollectionService collectionService;

    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseDto<?> createCollection(
            @Valid @RequestBody CreateCollectionRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        log.info("Creating collection by user: {}", currentUser.getUserId());
        return ResponseDto.success(collectionService.createCollection(request, currentUser));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseDto<?> updateCollection(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCollectionRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        log.info("Updating collection: {} by user: {}", id, currentUser.getUserId());
        return ResponseDto.success(collectionService.updateCollection(id, request, currentUser));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseDto<?> getCollectionById(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        log.info("Fetching collection: {} by user: {}", id, currentUser.getUserId());
        return ResponseDto.success(collectionService.getCollectionById(id, currentUser));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseDto<String> softDeleteCollection(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        log.info("Deleting collection: {} by user: {}", id, currentUser.getUserId());
        collectionService.softDeleteCollection(id, currentUser);
        return ResponseDto.success("Collection deleted successfully");
    }

    @GetMapping("/my-collections")
    @PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseDto<?> listMyCollections(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        log.info("Listing collections for user: {}", currentUser.getUserId());
        Pageable pageable = PageRequest.of(page, size);
        return ResponseDto.success(collectionService.listMyCollections(currentUser, pageable));
    }

    @GetMapping("/public")
    @PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseDto<?> listPublicCollections(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("Listing public collections");
        Pageable pageable = PageRequest.of(page, size);
        return ResponseDto.success(collectionService.listPublicCollections(pageable));
    }

    @GetMapping("/my-collections/count")
    @PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseDto<?> countMyCollections(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        log.info("Counting collections for user: {}", currentUser.getUserId());
        return ResponseDto.success(collectionService.countMyCollections(currentUser));
    }
}
