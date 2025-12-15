package SEP490.EduPrompt.dto.response.user;

import SEP490.EduPrompt.model.SubscriptionTier;
import jakarta.persistence.*;
import lombok.Builder;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;
import java.util.UUID;

@Builder
public record UserResponse (
        UUID id,
        UUID subscriptionTierId,
        UUID schoolId,
        String firstName,
        String lastName,
        String phoneNumber,
        String email,
        String role,
        Boolean isActive,
        Boolean isVerified,
        Instant createdAt,
        Instant updatedAt
){

}
