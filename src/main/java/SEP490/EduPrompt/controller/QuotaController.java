package SEP490.EduPrompt.controller;

import SEP490.EduPrompt.dto.response.ResponseDto;
import SEP490.EduPrompt.dto.response.quota.UserQuotaResponse;
import SEP490.EduPrompt.service.ai.QuotaService;
import SEP490.EduPrompt.service.auth.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

    @GetMapping("/my-quota")
    public ResponseDto<UserQuotaResponse> getMyQuota(
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        UserQuotaResponse result = quotaService.getUserQuota(currentUser.getUserId());
        return ResponseDto.success(result);
    }

    //this endpoint should be called by system admin only, yet just leave it like this for testing
    @GetMapping("/sync-user-quota/{userId}")
    public ResponseDto<String> syncUserQuota(@PathVariable UUID userId) {
        quotaService.syncUserQuotaWithSubscriptionTier(userId);
        return ResponseDto.success("sync user quota completed");
    }
}