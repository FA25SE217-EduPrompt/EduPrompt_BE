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
 * Generates an upload signature and related parameters for client-side Cloudinary uploads.
 *
 * @param type the FileType indicating the kind of file to be uploaded
 * @return an UploadSignatureResponse containing signature, timestamp, apiKey, cloudName, and uploadPreset
 */
    UploadSignatureResponse generateUploadSignature(FileType type);

    /**
 * Persist attachment metadata submitted after a successful Cloudinary upload and associate it with the current user.
 *
 * @param request      metadata produced by or required for the attachment (Cloudinary `url` and `publicId`, plus `fileType`, `fileName`, and `size`)
 * @param currentUser  the authenticated user who owns or created the attachment
 * @return             an AttachmentResponse containing the stored attachment's identifier, URL, and related metadata
 */
    AttachmentResponse createAttachment(AttachmentRequest request, UserPrincipal currentUser);

    /**
 * Retrieve attachments associated with a specific prompt version.
 *
 * @param promptVersionId the UUID identifying the prompt version
 * @return a list of AttachmentResponse objects for the specified prompt version; empty if none exist
 */
    List<AttachmentResponse> getAttachmentsByPromptVersion(UUID promptVersionId);

    /**
 * Delete the attachment identified by the given UUID.
 *
 * @param id the UUID of the attachment to delete
 */
    void deleteAttachment(UUID id);
}