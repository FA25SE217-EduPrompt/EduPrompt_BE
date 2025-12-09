package SEP490.EduPrompt.dto.request.school;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSchoolRequest(
        @NotBlank(message = "School name must not null")
        @Size(max = 500, message = "School name maximum 500 letters")
        String name,

        @Size(max = 1000, message = "Address maximum 1000 letter")
        String address,

        @Size(max = 50, message = "invalid phone number")
        String phoneNumber,

        @NotBlank(message = "District must not null")
        @Size(max = 200, message = "District maximum 200 letter")
        String district,

        @NotBlank(message = "Province must not null")
        @Size(max = 200, message = "Province maximum 200 letter")
        String province
) {
}
