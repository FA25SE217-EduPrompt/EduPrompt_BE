package SEP490.EduPrompt.dto.request.search;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.util.UUID;

@Builder
public record SemanticSearchRequest(
        @NotBlank(message = "Query is required")
        String query,

        @NotNull(message = "User ID is required")
        UUID userId,

        SearchContext context,

        Integer limit // default 10, max 20
) {
}
