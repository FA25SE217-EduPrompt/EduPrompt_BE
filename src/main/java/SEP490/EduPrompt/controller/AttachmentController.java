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
     * Generate a Cloudinary upload signature for client-side uploads.
     *
     * @param fileType the file type to sign; defaults to IMAGE when not provided
     * @return an UploadSignatureResponse containing the signature, timestamp, API key, cloud name, and upload preset
     */
    @GetMapping("/upload-signature")
    public ResponseDto<UploadSignatureResponse> getUploadSignature(
            @RequestParam(defaultValue = "IMAGE") FileType fileType
    ) {
        UploadSignatureResponse response = attachmentService.generateUploadSignature(fileType);
        return ResponseDto.success(response);
    }

    /**
     * Save attachment metadata after a successful client upload to Cloudinary.
     *
     * @param request contains attachment data such as url, publicId, fileType, fileName, size, and promptVersionId
     * @return the saved attachment details as an AttachmentResponse
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
     * Retrieve all attachments for the specified prompt version.
     *
     * @param promptVersionId the prompt version ID to fetch attachments for
     * @return the list of attachments for the specified prompt version
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
     * Delete an attachment by its identifier.
     *
     * @param id the UUID of the attachment to delete
     * @return a ResponseDto with no content
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseDto<Void> deleteAttachment(@PathVariable UUID id) {
        attachmentService.deleteAttachment(id);
        return ResponseDto.success(null);
    }
}