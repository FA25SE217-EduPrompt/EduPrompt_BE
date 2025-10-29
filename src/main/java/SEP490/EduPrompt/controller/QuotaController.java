package SEP490.EduPrompt.controller;

import SEP490.EduPrompt.dto.response.ResponseDto;
import SEP490.EduPrompt.dto.response.quota.UserQuotaResponse;
import SEP490.EduPrompt.service.ai.QuotaService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/quota")
@PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
public class QuotaController {
    private final QuotaService quotaService;

    //should strict this endpoint for user to view their own quota only, just leave this for testing first
    @GetMapping("/user/{userId}")
    public ResponseDto<UserQuotaResponse> getUserQuota(@PathVariable UUID userId) {
        UserQuotaResponse result = quotaService.getUserQuota(userId);
        return ResponseDto.success(result);
    }

    @GetMapping("/sync-user-quota/{userId}")
    public ResponseDto<String> syncUserQuota(@PathVariable UUID userId) {
        UUID freeTierSubId = UUID.fromString("1025743d-bf58-4fef-ac95-912c6b1037d8");
        quotaService.syncUserQuotaWithSubscriptionTier(userId, freeTierSubId);
        return ResponseDto.success("sync user quota completed");
    }
}