package SEP490.EduPrompt.controller;

import SEP490.EduPrompt.dto.request.search.SemanticSearchRequest;
import SEP490.EduPrompt.dto.response.ResponseDto;
import SEP490.EduPrompt.dto.response.search.SemanticSearchResponse;
import SEP490.EduPrompt.service.auth.UserPrincipal;
import SEP490.EduPrompt.service.search.SemanticSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
@Tag(name = "Semantic Search", description = "APIs for semantic search functionality")
public class SemanticSearchController {

    private final SemanticSearchService semanticSearchService;

    @Operation(summary = "Perform semantic search", description = "Search for prompts using natural language query")
    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseDto<SemanticSearchResponse> search(
            @Valid @RequestBody SemanticSearchRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        log.info("Received semantic search request from user: {} for query: {}", currentUser.getUserId(),
                request.query());

        // Ensure request has correct user ID from token
        SemanticSearchRequest secureRequest = SemanticSearchRequest.builder()
                .query(request.query())
                .limit(request.limit())
                .context(request.context())
                .userId(currentUser.getUserId())
                .username(request.username())
                .build();

        return ResponseDto.success(semanticSearchService.search(secureRequest));
    }
}
