package SEP490.EduPrompt.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Builder
@Data
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ResetPasswordRequest {
    @NotBlank(message = "Token is required")
    private String token;
    @NotBlank(message = "New password is required")
    private String newPassword;
}
