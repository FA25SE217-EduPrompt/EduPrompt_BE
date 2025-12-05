package SEP490.EduPrompt.service.prompt;

import SEP490.EduPrompt.dto.response.prompt.PagePromptResponse;
import SEP490.EduPrompt.dto.response.tradePoint.TradePointResponse;
import SEP490.EduPrompt.model.PointTransaction;
import SEP490.EduPrompt.service.auth.UserPrincipal;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface PromptTradeService {
    PagePromptResponse getTradeablePrompts(Pageable pageable);

    TradePointResponse tradePrompt(UUID promptId, UserPrincipal buyer);

    void makePromptTradeable(UUID promptId, UserPrincipal seller);
}
