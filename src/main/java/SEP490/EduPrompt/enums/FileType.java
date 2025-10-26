package SEP490.EduPrompt.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum FileType {
    // i should change those upload preset later
    IMAGE("eduprompt-image", "image"),
    VIDEO("eduprompt-video", "video"),
    DOCUMENT("eduprompt-doc", "raw"); // i like doing it raw :D

    private final String uploadPreset;
    private final String resourceType;
}