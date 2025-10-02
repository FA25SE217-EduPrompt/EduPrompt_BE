package SEP490.EduPrompt.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RegisterRequest {

    //TODO: validate input
    private String email;
    private String password;
    private String firstName;
    private String lastName;
    private String phoneNumber;
}
