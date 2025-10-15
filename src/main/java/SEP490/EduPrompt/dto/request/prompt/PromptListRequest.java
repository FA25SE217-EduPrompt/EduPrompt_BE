package SEP490.EduPrompt.dto.request.prompt;

import SEP490.EduPrompt.model.Tag;

import java.util.List;

public record PromptListRequest(
        String q,
        List<Tag> tags,    // list of tag.type and tag.value
        String visibility  //  "public" | "private"
) {
}
