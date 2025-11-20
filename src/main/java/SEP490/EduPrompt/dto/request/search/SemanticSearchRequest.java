package SEP490.EduPrompt.dto.request.search;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

import java.util.UUID;

@Builder
public record SemanticSearchRequest(
        @NotBlank(message = "Query is required")
        String query,

        UUID userId,

        String username,

        SearchContext context,

        Integer limit // default 10, max 20
) {
}
