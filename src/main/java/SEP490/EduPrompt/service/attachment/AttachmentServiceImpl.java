package SEP490.EduPrompt.service.attachment;

import SEP490.EduPrompt.dto.request.attachment.AttachmentRequest;
import SEP490.EduPrompt.dto.response.attachment.AttachmentResponse;
import SEP490.EduPrompt.dto.response.attachment.UploadSignatureResponse;
import SEP490.EduPrompt.enums.FileType;
import SEP490.EduPrompt.exception.auth.ResourceNotFoundException;
import SEP490.EduPrompt.exception.client.SignatureGenerationException;
import SEP490.EduPrompt.model.Attachment;
import SEP490.EduPrompt.model.PromptVersion;
import SEP490.EduPrompt.model.User;
import SEP490.EduPrompt.repo.AttachmentRepository;
import SEP490.EduPrompt.repo.PromptVersionRepository;
import SEP490.EduPrompt.repo.UserRepository;
import SEP490.EduPrompt.service.auth.UserPrincipal;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        UUID promptVersionId = request.getPromptVersionId();
        PromptVersion promptVersion = promptVersionRepository.findById(promptVersionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Prompt Version not found with id: " + promptVersionId));

        User creator = userRepository.getReferenceById(currentUser.getUserId());

        //TODO: might add quota limit : 5 file a day ; implement after quota service

        Attachment attachment = Attachment.builder()
                .url(request.getUrl())
                .publicId(request.getPublicId())
                .fileName(request.getFileName())
                .fileType(request.getFileType().name())
                .size(request.getSize())
                .promptVersion(promptVersion)
                .createdAt(Instant.now())
                .createdBy(creator)
                .build();

        Attachment savedAttachment = attachmentRepository.save(attachment);
        log.info("Saved attachment with id: {}", savedAttachment.getId());

        return mapToResponse(savedAttachment);
    }

    @Override
    public List<AttachmentResponse> getAttachmentsByPromptVersion(UUID promptVersionId) {
        List<Attachment> attachments = attachmentRepository
                .findByPromptVersionId(promptVersionId);

        return attachments.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
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

    private AttachmentResponse mapToResponse(Attachment attachment) {
        return AttachmentResponse.builder()
                .id(attachment.getId())
                .publicId(attachment.getPublicId())
                .url(attachment.getUrl())
                .fileName(attachment.getFileName())
                .fileType(attachment.getFileType())
                .size(attachment.getSize())
                .createdAt(attachment.getCreatedAt())
                .build();
    }
}
