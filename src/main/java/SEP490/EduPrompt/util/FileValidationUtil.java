package SEP490.EduPrompt.util;

import SEP490.EduPrompt.exception.generic.InvalidFileException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
public class FileValidationUtil {
    /**
     * Validate uploaded file
     */
    public static void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("File is empty or null");
        }

        // Check file size (max 10MB)
        long maxSize = 10 * 1024 * 1024; // 10MB
        if (file.getSize() > maxSize) {
            throw new InvalidFileException("File size exceeds maximum limit of 10MB");
        }

        // Check file type
        String contentType = file.getContentType();
        if (contentType == null) {
            throw new InvalidFileException("File content type is unknown");
        }

        // Allow common document formats
        if (!isAllowedFileType(contentType)) {
            throw new InvalidFileException(
                    "File type not supported. Allowed types: PDF, Word, Text, Images"
            );
        }

        log.info("File validation passed: {} ({})", file.getOriginalFilename(), contentType);
    }

    /**
     * Check if file type is allowed
     */
    public static boolean isAllowedFileType(String contentType) {
        return contentType.equals("application/pdf") ||
                contentType.equals("application/msword") ||
                contentType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document") ||
                contentType.equals("text/plain") ||
                contentType.startsWith("image/");
    }
}
