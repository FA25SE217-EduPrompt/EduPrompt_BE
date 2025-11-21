package SEP490.EduPrompt.dto.response.payment;

import lombok.*;

import java.util.UUID;

@Builder
@AllArgsConstructor
@Getter
@Setter
public class PaymentResponse {
    private final boolean success;               // true only if payment valid AND subscription applied
    private final String message;                // for frontend
    private final String vnpResponseCode;
    private final UUID subscriptionTierId;       // only present if success = true

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

    // getters
    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public String getVnpResponseCode() { return vnpResponseCode; }
    public UUID getSubscriptionTierId() { return subscriptionTierId; }
}
