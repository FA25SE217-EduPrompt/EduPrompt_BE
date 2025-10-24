package SEP490.EduPrompt.dto.request.prompt;


import jakarta.validation.constraints.Pattern;

import java.util.List;
import java.util.UUID;

public record PromptListRequest(
        String q,
        List<UUID> tags,
        @Pattern(regexp = "(private|public)", message = "Invalid visibility")// list of tag.type and tag.value
        String visibility  //  "public" | "private"
) {
}
