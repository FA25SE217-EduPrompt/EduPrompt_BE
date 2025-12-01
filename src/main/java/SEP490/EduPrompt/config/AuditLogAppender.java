package SEP490.EduPrompt.config;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import SEP490.EduPrompt.model.AuditLog;
import SEP490.EduPrompt.model.User;
import SEP490.EduPrompt.repo.AuditLogRepository;
import SEP490.EduPrompt.repo.UserRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
public class AuditLogAppender extends AppenderBase<ILoggingEvent> {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    @Autowired
    public AuditLogAppender(AuditLogRepository auditLogRepository, UserRepository userRepository) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
    }

    @Override
    protected void append(ILoggingEvent event) {
        // Only log INFO level and above, or customize as needed
        if (event.getLevel().isGreaterOrEqual(ch.qos.logback.classic.Level.INFO)) {
            String userIdStr = MDC.get("userId"); // Assume you set MDC.put("userId", user.getId().toString()) in logs or interceptors
            User user = null;
            if (userIdStr != null) {
                try {
                    UUID userId = UUID.fromString(userIdStr);
                    Optional<User> optionalUser = userRepository.findById(userId);
                    user = optionalUser.orElse(null);
                } catch (IllegalArgumentException e) {
                    // Invalid UUID, skip user
                }
            }

            String actionLog = event.getFormattedMessage(); // Or customize to include logger name, thread, etc.

            AuditLog auditLog = AuditLog.builder()
                    .user(user)
                    .actionLog(actionLog)
                    .createdAt(Instant.now())
                    .build();

            auditLogRepository.save(auditLog);
        }
    }
}