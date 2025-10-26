package SEP490.EduPrompt.service.attachment;

import SEP490.EduPrompt.dto.request.attachment.AttachmentRequest;
import SEP490.EduPrompt.dto.response.attachment.AttachmentResponse;
import SEP490.EduPrompt.dto.response.attachment.UploadSignatureResponse;
import SEP490.EduPrompt.enums.FileType;
import SEP490.EduPrompt.service.auth.UserPrincipal;

import java.util.List;
import java.util.UUID;

public interface AttachmentService {
    /**
     * Generate Cloudinary upload signature for client-side upload
     * @return UploadSignatureResponse containing signature, timestamp, apiKey, cloudName, uploadPreset
     */
    UploadSignatureResponse generateUploadSignature(FileType type);

    /**
     * Save attachment metadata after successful upload to Cloudinary
     * @param request AttachmentRequest containing url, publicId, fileType, fileName, size
     * @return AttachmentResponse with saved attachment details
     */
    AttachmentResponse createAttachment(AttachmentRequest request, UserPrincipal currentUser);

    /**
     * Get all attachments for a specific prompt version
     * @param promptVersionId UUID of the prompt version
     * @return List of AttachmentResponse
     */
    List<AttachmentResponse> getAttachmentsByPromptVersion(UUID promptVersionId);

    /**
     * Delete an attachment by ID
     * @param id UUID of the attachment
     */
    void deleteAttachment(UUID id);
}
