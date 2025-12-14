package SEP490.EduPrompt.dto.request.payment;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.UUID;

@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PaymentRequest {
    @Positive(message = "Amount must be positive")
    private long amount;

    private String bankCode;

    @Size(max = 255, message = "Order description must not exceed 255 characters")
    private String orderDescription;

    @NotNull(message = "Subscription tier ID is required")
    private UUID subscriptionTierId;
}
