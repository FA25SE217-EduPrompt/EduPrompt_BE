package SEP490.EduPrompt.controller;

import SEP490.EduPrompt.dto.request.attachment.AttachmentRequest;
import SEP490.EduPrompt.dto.response.ResponseDto;
import SEP490.EduPrompt.dto.response.attachment.AttachmentResponse;
import SEP490.EduPrompt.dto.response.attachment.UploadSignatureResponse;
import SEP490.EduPrompt.enums.FileType;
import SEP490.EduPrompt.service.attachment.AttachmentService;
import SEP490.EduPrompt.service.auth.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
@RequestMapping("/api/attachments")
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService attachmentService;

    /**
     * GET /api/attachments/upload-signature?fileType=IMAGE
     * Generate Cloudinary upload signature for client-side upload
     * fileType is upper-case ofc
     * @return UploadSignatureResponse containing signature, timestamp, apiKey, cloudName, uploadPreset
     */
    @GetMapping("/upload-signature")
    public ResponseDto<UploadSignatureResponse> getUploadSignature(
            @RequestParam(defaultValue = "IMAGE") FileType fileType
    ) {
        UploadSignatureResponse response = attachmentService.generateUploadSignature(fileType);
        return ResponseDto.success(response);
    }

    /**
     * POST /api/attachments
     * Save attachment metadata after successful upload to Cloudinary
     *
     * @param request AttachmentRequest containing url, publicId, fileType, fileName, size, promptVersionId
     * @return AttachmentResponse with saved attachment details
     */
    @PostMapping()
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseDto<AttachmentResponse> createAttachment(
            @Valid @RequestBody AttachmentRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        AttachmentResponse response = attachmentService.createAttachment(request, currentUser);
        return ResponseDto.success(response);
    }

    /**
     * GET /api/attachments/prompt-version/{promptVersionId}
     * Get all attachments for a specific prompt version
     *
     * @param promptVersionId UUID of the prompt version
     * @return List of AttachmentResponse
     */
    @GetMapping("/prompt-version/{promptVersionId}")
    public ResponseDto<List<AttachmentResponse>> getAttachmentsByPromptVersion(
            @PathVariable UUID promptVersionId
    ) {
        List<AttachmentResponse> responses = attachmentService
                .getAttachmentsByPromptVersion(promptVersionId);
        return ResponseDto.success(responses);
    }

    /**
     * DELETE /api/attachments/{id}
     * real Delete an attachment by ID
     * optional: delete on cloudinary as well :D
     * @param id UUID of the attachment
     * @return ResponseDto with no content
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseDto<Void> deleteAttachment(@PathVariable UUID id) {
        attachmentService.deleteAttachment(id);
        return ResponseDto.success(null);
    }
}
