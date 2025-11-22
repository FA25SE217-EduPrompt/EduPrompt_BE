package SEP490.EduPrompt.dto.response.tag;

import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder
public record BatchAddResultResponse(
        UUID entityId,                     // promptId or collectionId
        List<TagRelationResponse> added
) {
}
