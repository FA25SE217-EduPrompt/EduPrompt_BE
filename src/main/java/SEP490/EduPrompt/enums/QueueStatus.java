package SEP490.EduPrompt.enums;

import SEP490.EduPrompt.exception.auth.InvalidInputException;

public enum QueueStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED;

    public static QueueStatus parseQueueStatus(String qs) {
        if (qs == null || qs.isBlank()) {
            throw new InvalidInputException("Queue status is required. Allowed: PENDING, PROCESSING, COMPLETED, FAILED");
        }
        String normalized = qs.trim().toUpperCase().replace(' ', '_').replace('-', '_');
        try {
            return QueueStatus.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException("Invalid queue status: " + qs + ". Allowed: PENDING, PROCESSING, COMPLETED, FAILED");
        }
    }
}
