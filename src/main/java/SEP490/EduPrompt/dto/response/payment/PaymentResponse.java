package SEP490.EduPrompt.dto.response.payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Builder
@AllArgsConstructor
@Setter
public class PaymentResponse {
    private String RspCode;
    private String Message;
}
