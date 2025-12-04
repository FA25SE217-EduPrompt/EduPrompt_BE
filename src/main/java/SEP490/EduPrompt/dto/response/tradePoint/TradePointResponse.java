package SEP490.EduPrompt.dto.response.tradePoint;

import lombok.Builder;

import java.util.UUID;

@Builder
public record TradePointResponse (
        boolean success,
        String message,
        UUID promptId
){
}
