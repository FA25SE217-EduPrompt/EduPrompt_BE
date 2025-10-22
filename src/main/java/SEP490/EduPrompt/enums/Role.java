package SEP490.EduPrompt.enums;

import SEP490.EduPrompt.exception.auth.InvalidInputException;

public enum Role {
    TEACHER,
    SCHOOL_ADMIN,
    SYSTEM_ADMIN;

    public static Role parseRole(String r) {
        if (r == null) throw new InvalidInputException("Invalid role: " + r);
        try {
            return Role.valueOf(r.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException("Invalid role: " + r);
        }
    }
}
