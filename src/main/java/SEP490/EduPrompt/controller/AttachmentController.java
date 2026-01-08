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
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
     *
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
     * DELETE /api/attachments/{id}
     * real Delete an attachment by ID
     * optional: delete on cloudinary as well :D
     *
     * @param id UUID of the attachment
     * @return ResponseDto with no content
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseDto<Void> deleteAttachment(@PathVariable UUID id) {
        attachmentService.deleteAttachment(id);
        return ResponseDto.success(null);
    }

    /**
     * POST /api/attachments/upload
     * Server-side upload: Receives file, uploads to Cloudinary, and saves entity.
     * Does NOT require promptVersionId.
     *
     * @param file The file binary to upload
     * @param currentUser The authenticated user
     * @return AttachmentResponse
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseDto<AttachmentResponse> uploadFileAttachment(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        AttachmentResponse response = attachmentService.uploadAttachment(file, currentUser);
        return ResponseDto.success(response);
    }

//    @GetMapping("/attachment-url")
//    public ResponseDto<String> getAttachmentUrl(
//            @RequestParam String publicId,
//            @AuthenticationPrincipal UserPrincipal currentUser
//    ) {
//        return ResponseDto.success(attachmentService.generateTimeLimitedUrl(publicId));
//    }
}
