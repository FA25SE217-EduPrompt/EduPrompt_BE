package SEP490.EduPrompt.dto.request;

import jakarta.validation.constraints.NotBlank;

/***
 * Add tag to collection/prompt if already existed, if none, create a new one and add
 * @param type
 * @param value
 */

public record AddTagRequest(
        @NotBlank
        String type,
        @NotBlank
        String value
) {
}
