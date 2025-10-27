package SEP490.EduPrompt.enums;

import SEP490.EduPrompt.exception.auth.InvalidInputException;
import lombok.Getter;

import java.util.Locale;

@Getter
public enum AiModel {
    GPT_4O_MINI("gpt-4o-mini"),
    CLAUDE_3_5_SONNET("claude-3.5-sonnet"),
    GEMINI_2_0_FLASH("gemini-2.5-flash");

    private final String name;

    AiModel(String name) {
        this.name = name;
    }

    public static AiModel parseAiModel(String a) {
        if (a == null || a.isBlank()) {
            throw new IllegalArgumentException("Model name cannot be null or blank");
        }
        String normalized = a
                .toUpperCase(Locale.ROOT)
                .replace("-", "_");
        try {
            return AiModel.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException("Invalid model name: " + a + ". Allowed: gpt-4o-mini, claude-3.5-sonnet, gemini-2.5-flash");
        }
    }
}
