package SEP490.EduPrompt.service.attachment;

import SEP490.EduPrompt.dto.request.attachment.AttachmentRequest;
import SEP490.EduPrompt.dto.response.attachment.AttachmentResponse;
import SEP490.EduPrompt.dto.response.attachment.UploadSignatureResponse;
import SEP490.EduPrompt.enums.FileType;
import SEP490.EduPrompt.exception.auth.ResourceNotFoundException;
import SEP490.EduPrompt.exception.client.CloudinaryUploadFailException;
import SEP490.EduPrompt.exception.client.FileUploadFailException;
import SEP490.EduPrompt.exception.client.SignatureGenerationException;
import SEP490.EduPrompt.model.Attachment;
import SEP490.EduPrompt.model.PromptVersion;
import SEP490.EduPrompt.model.User;
import SEP490.EduPrompt.repo.AttachmentRepository;
import SEP490.EduPrompt.repo.PromptVersionRepository;
import SEP490.EduPrompt.repo.UserRepository;
import SEP490.EduPrompt.service.auth.UserPrincipal;
import com.cloudinary.AuthToken;
import com.cloudinary.Cloudinary;
import com.cloudinary.Url;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AttachmentServiceImpl implements AttachmentService {

    private final Cloudinary cloudinary;
    private final AttachmentRepository attachmentRepository;
    private final PromptVersionRepository promptVersionRepository;
    private final UserRepository userRepository;

    @Value("${cloudinary.cloud-name}")
    private String cloudName;

    @Value("${cloudinary.api-key}")
    private String apiKey;

    @Override
    public UploadSignatureResponse generateUploadSignature(FileType fileType) {
        try {
            long timestamp = System.currentTimeMillis() / 1000;

            String uploadPreset = fileType.getUploadPreset();
            String resourceType = fileType.getResourceType();

            Map<String, Object> paramsToSign = new HashMap<>();
            paramsToSign.put("timestamp", timestamp);

            if (uploadPreset != null && !uploadPreset.isBlank()) {
                paramsToSign.put("upload_preset", uploadPreset);
            }
            // paramsToSign.put("folder", "attachments");
            // paramsToSign.put("resource_type", "auto");

            // generate signature
            String signature = cloudinary.apiSignRequest(
                    paramsToSign,
                    cloudinary.config.apiSecret
            );

            return UploadSignatureResponse.builder()
                    .signature(signature)
                    .timestamp(timestamp)
                    .apiKey(apiKey)
                    .cloudName(cloudName)
                    .uploadPreset(uploadPreset)
                    .resourceType(resourceType)
                    .build();
        } catch (Exception e) {
            throw new SignatureGenerationException("Failed to generate upload signature : " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public AttachmentResponse createAttachment(AttachmentRequest request, UserPrincipal currentUser) {

        User creator = userRepository.getReferenceById(currentUser.getUserId());

        //TODO: might add quota limit : 5 file a day ; implement after quota service

        Attachment attachment = Attachment.builder()
                .url(request.getUrl())
                .publicId(request.getPublicId())
                .fileName(request.getFileName())
                .fileType(request.getFileType().name())
                .size(request.getSize())
                .createdAt(Instant.now())
                .createdBy(creator)
                .build();

        Attachment savedAttachment = attachmentRepository.save(attachment);
        log.info("Saved attachment with id: {}", savedAttachment.getId());

        return mapToResponse(savedAttachment);
    }

    @Override
    @Transactional
    public void deleteAttachment(UUID id) {
        Attachment attachment = attachmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Attachment not found with id: " + id));

        // optionally: delete from cloudinary
        try {
            cloudinary.uploader().destroy(
                    attachment.getPublicId(),
                    ObjectUtils.emptyMap()
            );
        } catch (Exception e) {
            log.info("Failed to delete attachment on cloudinary with id: {}", id);
            // no need to throw any exceptions here, just log
        }

        // delete for real (not soft-delete)
        attachmentRepository.delete(attachment);
        log.info("Deleted attachment: {}", attachment.getId());
    }

    @Override
    @Transactional
    public AttachmentResponse uploadAttachment(MultipartFile file, UserPrincipal currentUser) {
        try {
            String resourceType = determineResourceType(file);
            Map<String, Object> uploadParams = new HashMap<>();
            uploadParams.put("folder", "attachments");
            uploadParams.put("resource_type", resourceType);
            uploadParams.put("timestamp", System.currentTimeMillis() / 1000L);
            uploadParams.put("type", "authenticated");
            String signature = cloudinary.apiSignRequest(
                    uploadParams,
                    cloudinary.config.apiSecret
            );
            uploadParams.put("api_key", apiKey);
            uploadParams.put("signature", signature);

            // "resource_type": "auto" allows Cloudinary to detect if it's image, video, or raw (pdf, doc)
            Map uploadResult = cloudinary.uploader().upload(file.getBytes(), uploadParams);
            String publicId = (String) uploadResult.get("public_id");
            String signedUrl = cloudinary.url()
                    .resourceType(resourceType)
                    .type("authenticated")
                    .signed(true)
                    .generate(publicId);

            Long size = Long.valueOf(String.valueOf(uploadResult.get("bytes")));
            // format is usually the extension (e.g., "jpg", "pdf")
            String format = (String) uploadResult.get("resource_type");

            User creator = userRepository.getReferenceById(currentUser.getUserId());

            Attachment attachment = Attachment.builder()
                    .url(signedUrl)
                    .publicId(publicId)
                    .fileName(file.getOriginalFilename())
                    .fileType(format)
                    .size(size)
                    .createdAt(Instant.now())
                    .createdBy(creator)
                    .build();

            Attachment savedAttachment = attachmentRepository.save(attachment);
            log.info("Uploaded and saved attachment with id: {}", savedAttachment.getId());

            return mapToResponse(savedAttachment);

        } catch (FileUploadFailException e) {
            throw e;
        } catch (Exception ex) {
            throw new CloudinaryUploadFailException("Cloudinary upload failed: " + ex.getMessage(), ex);
        }
    }

//    @Override
//    public String generateTimeLimitedUrl(String publicId) {
//        // 1. Fetch attachment to get the correct resource type (image, video, or raw)
//        // Using the wrong resource type will result in a 404 or invalid signature
//        Attachment attachment = attachmentRepository.findByPublicId(publicId)
//                .orElseThrow(() -> new ResourceNotFoundException("Attachment not found with publicId: " + publicId));
//
//        long startTime = System.currentTimeMillis() / 1000L;
//        long expirationSeconds = startTime + 1800;
//
//        // 2. Create the AuthToken object
//        // Use the key specifically meant for token-based auth
//        AuthToken token = new AuthToken(cloudinary.config.apiSecret)
//                .startTime(startTime)
//                .duration(expirationSeconds);
//
//        // 3. Generate the URL
//        return cloudinary.url()
//                .resourceType(attachment.getFileType())
//                .type("authenticated")
//                .authToken(token) // Pass the AuthToken object here
//                .generate(attachment.getPublicId());
//    }



    /**
     * Helper to determine if the file should be treated as 'image', 'video', or 'raw'.
     * 'image' type allows PDFs to be displayed inline instead of downloaded.
     */
    private String determineResourceType(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null) return "auto"; // Fallback

        if (contentType.startsWith("image") || contentType.equals("application/pdf")) {
            return "image"; // Treat PDF as image so it displays inline
        } else if (contentType.startsWith("video")) {
            return "video";
        }

        return "raw"; // Zip, Docx, etc. will properly download
    }



    private AttachmentResponse mapToResponse(Attachment attachment) {
        return AttachmentResponse.builder()
                .id(attachment.getId())
                .url(attachment.getUrl())
                .publicId(attachment.getPublicId())
                .fileName(attachment.getFileName())
                .fileType(attachment.getFileType())
                .size(attachment.getSize())
                .createdAt(attachment.getCreatedAt())
                .build();
    }
}
