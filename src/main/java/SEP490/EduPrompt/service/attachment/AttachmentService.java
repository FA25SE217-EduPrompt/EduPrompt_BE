package SEP490.EduPrompt.service.attachment;

import SEP490.EduPrompt.dto.request.attachment.AttachmentRequest;
import SEP490.EduPrompt.dto.response.attachment.AttachmentResponse;
import SEP490.EduPrompt.dto.response.attachment.UploadSignatureResponse;
import SEP490.EduPrompt.enums.FileType;
import SEP490.EduPrompt.service.auth.UserPrincipal;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface AttachmentService {
    /**
     * Generate Cloudinary upload signature for client-side upload
     *
     * @return UploadSignatureResponse containing signature, timestamp, apiKey, cloudName, uploadPreset
     */
    UploadSignatureResponse generateUploadSignature(FileType type);

    /**
     * Save attachment metadata after successful upload to Cloudinary
     *
     * @param request AttachmentRequest containing url, publicId, fileType, fileName, size
     * @return AttachmentResponse with saved attachment details
     */
    AttachmentResponse createAttachment(AttachmentRequest request, UserPrincipal currentUser);

    /**
     * Delete an attachment by ID
     *
     * @param id UUID of the attachment
     */
    void deleteAttachment(UUID id);

    /**
     * Uploads a file to Cloudinary and saves the attachment metadata.
     *
     * @param file        The binary file
     * @param currentUser The user uploading the file
     * @return The saved attachment response
     */
    AttachmentResponse uploadAttachment(MultipartFile file, UserPrincipal currentUser);

//    String generateTimeLimitedUrl(String publicId);
}
