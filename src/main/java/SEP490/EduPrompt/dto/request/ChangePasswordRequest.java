package SEP490.EduPrompt.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Builder
@Data
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ChangePasswordRequest {
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "Old password is required")
    @Size(min = 3, max = 32, message = "Old password must be between 3 and 32 characters")
    private String oldPassword;

    @NotBlank(message = "New password is required")
    @Size(min = 3, max = 32, message = "New password must be between 3 and 32 characters")
    private String newPassword;
}
