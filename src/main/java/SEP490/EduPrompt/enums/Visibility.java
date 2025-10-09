package SEP490.EduPrompt.enums;

import SEP490.EduPrompt.exception.auth.InvalidInputException;

public enum Visibility {
    PRIVATE,
    PUBLIC,
    SCHOOL,
    GROUP;

    public static Visibility parseVisibility(String v) {
        if (v == null) return Visibility.PUBLIC; // default
        try {
            return Visibility.valueOf(v.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException("Invalid visibility: " + v);
        }
    }
}

