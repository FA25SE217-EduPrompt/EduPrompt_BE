package SEP490.EduPrompt.controller;

import SEP490.EduPrompt.dto.request.prompt.CreatePromptRequest;
import SEP490.EduPrompt.dto.response.ResponseDto;
import SEP490.EduPrompt.dto.response.prompt.DetailPromptResponse;
import SEP490.EduPrompt.dto.response.prompt.PagePromptResponse;
import SEP490.EduPrompt.dto.response.tradePoint.TradePointResponse;
import SEP490.EduPrompt.service.auth.UserPrincipal;
import SEP490.EduPrompt.service.prompt.PromptTradeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/marketplace")
@RequiredArgsConstructor
@Slf4j
public class PromptTradeController {
    private final PromptTradeService promptTradeService;

    @GetMapping("/tradable")
    @PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseDto<PagePromptResponse> getTradablePrompt(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        log.info("Get all tradable prompt by user: {}", currentUser.getUserId());
        Pageable pageable = PageRequest.of(page, size);
        PagePromptResponse response = promptTradeService.getTradeablePrompts(pageable);
        return ResponseDto.success(response);
    }
    @PostMapping("/trade/{promptId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseDto<TradePointResponse> tradePrompt(
            @PathVariable UUID promptId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        log.info("Trading prompt {} by user: {}",promptId, currentUser.getUserId());
        TradePointResponse response = promptTradeService.tradePrompt(promptId, currentUser);
        return ResponseDto.success(response);
    }
}
