package SEP490.EduPrompt.service.prompt;

import SEP490.EduPrompt.dto.response.prompt.PagePromptResponse;
import SEP490.EduPrompt.dto.response.prompt.PromptSummaryResponse;
import SEP490.EduPrompt.dto.response.tradePoint.TradePointResponse;
import SEP490.EduPrompt.enums.Visibility;
import SEP490.EduPrompt.exception.auth.ResourceNotFoundException;
import SEP490.EduPrompt.exception.generic.InvalidActionException;
import SEP490.EduPrompt.model.PointTransaction;
import SEP490.EduPrompt.model.Prompt;
import SEP490.EduPrompt.model.User;
import SEP490.EduPrompt.model.UserQuota;
import SEP490.EduPrompt.repo.PointTransactionRepository;
import SEP490.EduPrompt.repo.PromptRepository;
import SEP490.EduPrompt.repo.UserQuotaRepository;
import SEP490.EduPrompt.repo.UserRepository;
import SEP490.EduPrompt.service.auth.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PromptTradeServiceImpl implements PromptTradeService {


    private final PromptRepository promptRepository;

    private final UserRepository userRepository;

    private final PointTransactionRepository pointTransactionRepository;

    private final UserQuotaRepository userQuotaRepository;

    @Override
    public PagePromptResponse getTradeablePrompts(Pageable pageable) {
        Page<Prompt> promptPage = promptRepository.findByIsTradeTrue(pageable);
        List<PromptSummaryResponse> content = promptPage.getContent().stream()
                .map(this::mapToSummary)
                .collect(Collectors.toList());

        return PagePromptResponse.builder()
                .content(content)
                .pageSize(promptPage.getSize())
                .pageNumber(promptPage.getNumber())
                .totalElements(promptPage.getTotalElements())
                .totalPages(promptPage.getTotalPages())
                .build();
    }

    @Override
    public TradePointResponse tradePrompt(UUID promptId, UserPrincipal buyers) {
        Prompt prompt = promptRepository.findById(promptId)
                .orElseThrow(() -> new ResourceNotFoundException("Prompt not found"));

        if (!prompt.getIsTrade()) {
            throw new ResourceNotFoundException("Prompt is not available for trade");
        }

        User seller = prompt.getUser();
        if (seller == null || seller.getId().equals(buyers.getUserId())) {
            throw new ResourceNotFoundException("Invalid trade: cannot trade with self or invalid seller");
        }

        User buyer = userRepository.getReferenceById(buyers.getUserId());

        UserQuota buyerQuota = userQuotaRepository.findByUserId(buyers.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Buyer not found"));

        UserQuota sellerQuota = userQuotaRepository.findByUserId(seller.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Seller not found"));

        if (buyerQuota.getPointsRemaining() <= 0) {
            throw new InvalidActionException("Insufficient points for trade");
        }

        // Create transaction
        PointTransaction transaction = new PointTransaction();
        transaction.setSender(buyer);
        transaction.setReceiver(seller);
        transaction.setAmount(1L);
        transaction.setStatus("completed");
        transaction.setNote("Trade for prompt " + promptId);
        transaction.setPrompt(prompt);
        transaction.setCreatedAt(OffsetDateTime.now());
        pointTransactionRepository.save(transaction);

        buyerQuota.setPointsRemaining(buyerQuota.getPointsRemaining() - 1);
        userQuotaRepository.save(buyerQuota);
        sellerQuota.setPointsRemaining(sellerQuota.getPointsRemaining() + 1);
        userQuotaRepository.save(sellerQuota);

        // Change prompt ownership
        prompt.setUser(buyer);
        prompt.setUserId(buyer.getId());
        prompt.setIsTrade(false);
        prompt.setVisibility(Visibility.PRIVATE.name());
        prompt.setShareToken(null);
        prompt.setCollection(null);
        prompt.setCollectionId(null);
        promptRepository.save(prompt);

        return TradePointResponse.builder()
                .success(true)
                .message("Trade completed successfully")
                .promptId(promptId)
                .build();
    }

    //HELPER
    private PromptSummaryResponse mapToSummary(Prompt prompt) {
        String ownerName = "";
        User owner = prompt.getUser();
        if (owner != null) {
            ownerName = owner.getFirstName() + " " + owner.getLastName();
        }
        return new PromptSummaryResponse(
                prompt.getId(),
                prompt.getTitle(),
                prompt.getDescription(),
                ownerName,
                prompt.getAvgRating()
        );
    }
}
