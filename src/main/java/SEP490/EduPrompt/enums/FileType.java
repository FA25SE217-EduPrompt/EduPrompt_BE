package SEP490.EduPrompt.enums;

import SEP490.EduPrompt.exception.auth.InvalidInputException;
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

    public static FileType parseFileType(String f) {
        if (f == null || f.isBlank()) {
            throw new InvalidInputException("FileType is required. Allowed: IMAGE, VIDEO, DOCUMENT");
        }
        String normalized = f.trim().toUpperCase().replace(' ', '_').replace('-', '_');
        try {
            return FileType.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException("Invalid fileType: " + f + ". Allowed: IMAGE, VIDEO, DOCUMENT");
        }
    }
}