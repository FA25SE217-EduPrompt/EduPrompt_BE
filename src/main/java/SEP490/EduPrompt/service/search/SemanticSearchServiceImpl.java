package SEP490.EduPrompt.service.search;

import SEP490.EduPrompt.dto.request.search.SemanticSearchRequest;
import SEP490.EduPrompt.dto.response.search.GroundingChunk;
import SEP490.EduPrompt.dto.response.search.SearchResultItem;
import SEP490.EduPrompt.dto.response.search.SemanticSearchResponse;
import SEP490.EduPrompt.exception.auth.ResourceNotFoundException;
import SEP490.EduPrompt.model.Prompt;
import SEP490.EduPrompt.model.SemanticSearchLog;
import SEP490.EduPrompt.model.User;
import SEP490.EduPrompt.repo.PromptRepository;
import SEP490.EduPrompt.repo.SemanticSearchLogRepository;
import SEP490.EduPrompt.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SemanticSearchServiceImpl implements SemanticSearchService {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 20;
    private final GeminiClientService geminiClientService;
    private final PromptRepository promptRepository;
    private final SemanticSearchLogRepository semanticSearchLogRepository;
    private final UserRepository userRepository;
    @Value("${gemini.file-search-store}")
    private String fileSearchStoreName;

    @Override
    @Transactional(readOnly = true)
    public SemanticSearchResponse search(SemanticSearchRequest request) {
        long startTime = System.currentTimeMillis();

        log.info("Semantic search request from user {} with query: {}",
                request.userId(), request.query());

        String enrichedQuery = enrichQuery(request);

        int limit = determineLimit(request.limit());

        List<GroundingChunk> chunks = geminiClientService.searchDocuments(
                fileSearchStoreName,
                enrichedQuery,
                limit * 2);

        Map<String, List<GroundingChunk>> chunksByDocument = chunks.stream()
                .collect(Collectors.groupingBy(GroundingChunk::documentId));

        long executionTime = System.currentTimeMillis() - startTime;
        List<SearchResultItem> results = buildSearchResults(chunksByDocument, limit, request.userId(),
                request.username());

        logSearch(request, results.size(), executionTime);

        String searchId = UUID.randomUUID().toString();

        log.info("Search {} completed in {}ms, found {} results",
                searchId, executionTime, results.size());

        return SemanticSearchResponse.builder()
                .results(results)
                .totalFound(results.size())
                .searchId(searchId)
                .executionTimeMs((int) executionTime)
                .build();
    }

    /**
     * Enrich query with context (tags, visibility filters)
     */
    private String enrichQuery(SemanticSearchRequest request) {
        StringBuilder enriched = new StringBuilder(request.query());

        if (request.context() != null) {
            // Add tags to query
            if (request.context().tags() != null &&
                    !request.context().tags().isEmpty()) {
                enriched.append(" ")
                        .append(String.join(" ", request.context().tags()));
            }

            // Add current prompt context (if provided)
            if (request.context().currentPrompt() != null &&
                    !request.context().currentPrompt().isBlank()) {
                enriched.append(" Similar to: ")
                        .append(request.context().currentPrompt(), 0, Math.min(200,
                                request.context().currentPrompt().length()));
            }
        }

        String enrichedQuery = enriched.toString().trim();
        log.debug("Enriched query: {}", enrichedQuery);
        return enrichedQuery;
    }

    /**
     * Determine result limit (default 10, max 20)
     */
    private int determineLimit(Integer requestedLimit) {
        if (requestedLimit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.min(requestedLimit, MAX_LIMIT);
    }

    /**
     * Build search results from grounding chunks
     */
    private List<SearchResultItem> buildSearchResults(
            Map<String, List<GroundingChunk>> chunksByDocument,
            int limit,
            UUID userId,
            String userName) {

        List<SearchResultItem> results = new ArrayList<>();

        for (Map.Entry<String, List<GroundingChunk>> entry : chunksByDocument.entrySet()) {
            String documentId = entry.getKey();
            List<GroundingChunk> chunks = entry.getValue();

            // format: fileSearchStores/{store}/documents/{doc}
            Optional<Prompt> promptOpt = promptRepository.findByGeminiFileIdStartingWith(documentId);
            if (promptOpt.isPresent() && promptOpt.get().getIsDeleted()) {
                log.info("Prompt {} not found or deleted, skipping", promptOpt.get().getId());
                continue;
            }

            Prompt prompt = promptOpt.orElseThrow();

            // Calculate aggregate score (max score from all chunks)
            Double maxScore = chunks.stream()
                    .map(GroundingChunk::confidenceScore)
                    .max(Double::compareTo)
                    .orElse(0.0);

            // Get best matching snippet
            String bestSnippet = chunks.stream()
                    .max(Comparator.comparing(GroundingChunk::confidenceScore))
                    .map(GroundingChunk::text)
                    .orElse("");

            // Build reasoning
            String reasoning = buildReasoning(prompt, maxScore, chunks.size());

            results.add(SearchResultItem.builder()
                    .promptId(prompt.getId())
                    .title(prompt.getTitle())
                    .description(prompt.getDescription())
                    .relevanceScore(maxScore)
                    .matchedSnippet(truncateSnippet(bestSnippet, 200))
                    .reasoning(reasoning)
                    .visibility(prompt.getVisibility())
                    .createdBy(userId)
                    .createdByName(userName)
                    .averageRating(4 + Math.random()) // wait for prompt rating
                    .build());
        }

        // Sort by relevance score (descending)
        results.sort(Comparator.comparing(SearchResultItem::relevanceScore).reversed());

        // Limit results
        if (results.size() > limit) {
            results = results.subList(0, limit);
        }

        return results;
    }

    /**
     * Build reasoning text for why this prompt matched
     */
    private String buildReasoning(Prompt prompt, Double score, int chunkCount) {
        List<String> reasons = new ArrayList<>();

        reasons.add(String.format("Relevance: %.0f%%", score * 100));

        if (chunkCount > 1) {
            reasons.add(String.format("%d matching sections", chunkCount));
        }

        if (prompt.getVisibility() != null) {
            reasons.add("Visibility: " + prompt.getVisibility());
        }

        // TODO: Add more reasoning based on tags, ratings, usage

        return String.join(" | ", reasons);
    }

    /**
     * Truncate snippet to max length
     */
    private String truncateSnippet(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    /**
     * Log search for analytics
     */
    private void logSearch(SemanticSearchRequest request, int resultCount, long executionTime) {

        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new ResourceNotFoundException("user not found"));
        SemanticSearchLog searchLog = SemanticSearchLog.builder()
                .user(user)
                .query(request.query())
                .filters(null) // add need filter for this
                .executionTimeMs((int) executionTime)
                .resultsCount(resultCount)
                .build();
        semanticSearchLogRepository.save(searchLog);

        log.info("Search logged: user={}, query={}, results={}",
                request.userId(), request.query(), resultCount);
    }

}
