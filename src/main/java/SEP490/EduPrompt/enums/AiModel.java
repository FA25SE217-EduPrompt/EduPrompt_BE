package SEP490.EduPrompt.enums;

import lombok.Getter;

@Getter
public enum AiModel {
    GPT_4O_MINI("gpt-4o-mini"),
    CLAUDE_3_5_SONNET("claude-3.5-sonnet"),
    GEMINI_2_0_FLASH("gemini-2.0-flash");

    private final String name;

    AiModel(String name) {
        this.name = name;
    }
}
