package SEP490.EduPrompt.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/***
 * Add tag to collection/prompt if already existed, if none, create a new one and add
 * @param type
 * @param value
 */

public record AddTagRequest(
        @NotBlank
        @Size(max = 50)
        String type,

        @Size(max = 255)
        @NotBlank
        String value
) {
}
