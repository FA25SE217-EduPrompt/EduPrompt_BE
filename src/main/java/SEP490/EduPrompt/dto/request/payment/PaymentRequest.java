package SEP490.EduPrompt.dto.request.payment;

import lombok.*;

import java.util.UUID;

@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PaymentRequest {
    private long amount;
    private String bankCode;
    private String orderDescription;
    private UUID subscriptionTierId;
}
