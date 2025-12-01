package SEP490.EduPrompt.config;

import ch.qos.logback.classic.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import javax.annotation.PostConstruct;

@Configuration
public class LogbackConfig {

    private final AuditLogAppender auditLogAppender;

    @Autowired
    public LogbackConfig(AuditLogAppender auditLogAppender) {
        this.auditLogAppender = auditLogAppender;
    }

    @PostConstruct
    public void init() {
        Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        auditLogAppender.setContext(rootLogger.getLoggerContext());
        auditLogAppender.start();
        rootLogger.addAppender(auditLogAppender);
    }
}
