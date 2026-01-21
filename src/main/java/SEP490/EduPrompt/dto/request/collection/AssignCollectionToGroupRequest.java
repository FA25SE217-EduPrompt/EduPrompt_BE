package SEP490.EduPrompt.dto.request.collection;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AssignCollectionToGroupRequest(
        @NotNull(message = "Collection ID is required")
        UUID collectionId,

        @NotNull(message = "Group ID is required")
        UUID groupId
) {
}