package SEP490.EduPrompt.service.auth;

import org.thymeleaf.context.Context;

public interface EmailService {
    void sendHtmlEmail(String to, String subject, String templateName, Context ctx);
    void sendVerificationEmail(String to, String name, String token);
    void sendWelcomeEmail(String to, String name);
    void sendResetPasswordEmail(String to, String name, String token, int expirationMinutes);
}
