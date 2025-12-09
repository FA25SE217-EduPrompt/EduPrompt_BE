package SEP490.EduPrompt.dto.response.search;

import lombok.Builder;

import java.time.Instant;

@Builder
public record FileSearchStoreResponse(
        String storeId, // actually this is its name
        String displayName,
        Instant createdAt,
        Instant updatedAt,
        Long activeDocumentCount
) {
}
