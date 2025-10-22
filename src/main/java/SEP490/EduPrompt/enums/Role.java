package SEP490.EduPrompt.enums;

import SEP490.EduPrompt.exception.auth.InvalidInputException;

public enum Role {
    TEACHER,
    SCHOOL_ADMIN,
    SYSTEM_ADMIN;

    public static Role parseRole(String r) {
        if (r == null || r.isBlank()) {
            throw new InvalidInputException("Role is required. Allowed: TEACHER, SCHOOL_ADMIN, SYSTEM_ADMIN");
        }
        String normalized = r.trim().toUpperCase().replace(' ', '_').replace('-', '_');
        try {
            return Role.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException("Invalid role: " + r + ". Allowed: TEACHER, SCHOOL_ADMIN, SYSTEM_ADMIN");
        }
    }
}
