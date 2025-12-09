package SEP490.EduPrompt.dto.response.payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
@Setter
public class PaymentResponse {
    private boolean success;               // true only if payment valid AND subscription applied
    private String message;                // for frontend
    private String vnpResponseCode;
    private UUID subscriptionTierId;       // only present if success = true

    // Success case
    public PaymentResponse(boolean success, String message, UUID subscriptionTierId) {
        this.success = success;
        this.message = message;
        this.vnpResponseCode = "00";
        this.subscriptionTierId = subscriptionTierId;
    }

    // Failure case
    public PaymentResponse(boolean success, String message, String vnpResponseCode) {
        this.success = success;
        this.message = message;
        this.vnpResponseCode = vnpResponseCode;
        this.subscriptionTierId = null;
    }
}
