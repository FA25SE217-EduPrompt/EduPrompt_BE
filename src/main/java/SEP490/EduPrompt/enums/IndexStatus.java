package SEP490.EduPrompt.enums;

import SEP490.EduPrompt.exception.auth.InvalidInputException;

public enum IndexStatus {
    INDEXED,
    FAILED,
    SKIPPED,
    PENDING;

    public static IndexStatus parseIndex(String i) {
        if (i == null || i.isBlank()) {
            throw new InvalidInputException("Index status is required. Allowed: INDEXED, SKIPPED, FAILED, PENDING");
        }
        String normalized = i.trim().toUpperCase().replace(' ', '_').replace('-', '_');
        try {
            return IndexStatus.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException("Invalid Index status : " + i + ". Allowed: INDEXED, SKIPPED, FAILED, PENDING");
        }
    }
}
