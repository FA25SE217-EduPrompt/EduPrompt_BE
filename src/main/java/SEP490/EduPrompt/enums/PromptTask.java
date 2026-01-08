package SEP490.EduPrompt.enums;

import lombok.Getter;

@Getter
public enum PromptTask {
    LESSON_PLAN("giao_an", "Giáo án"),
    SLIDE("slide", "Slide bài giảng"),
    TEST("de_kiem_tra", "Đề kiểm tra"),
    TEST_MATRIX("ma_tran_de", "Ma trận đề"),
    GROUP_ACTIVITY("hoat_dong_nhom", "Hoạt động nhóm");

    private final String value;
    private final String displayName;

    PromptTask(String value, String displayName) {
        this.value = value;
        this.displayName = displayName;
    }

}
