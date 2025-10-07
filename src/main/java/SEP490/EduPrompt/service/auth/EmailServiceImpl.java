package SEP490.EduPrompt.service.auth;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private static final String APP_NAME = "EduPrompt";
    private static final String CONTEXT_PATH = "/BE";
    private static final String HOME_PATH = "/"; //frontend url here :v
    private static final String VERIFY_PATH = "/api/auth/verify-email";
    private static final String RESET_PATH = "/api/auth/reset-password";

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;

    @Value("${app.base-url}")
    private String baseUrl;

    @Value("${spring.mail.username}")
    private String from;

    private void sendHtmlEmail(String to, String subject, String templateName, Context ctx) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, "utf-8");
            String html = templateEngine.process(templateName, ctx);
            helper.setText(html, true);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setFrom(from);
            mailSender.send(message);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send email", e);
        }
    }

    public void sendVerificationEmail(String to, String name, String token) {
        String link = baseUrl + CONTEXT_PATH + VERIFY_PATH + "?token=" + token;

        Context ctx = new Context();
        ctx.setVariable("name", name);
        ctx.setVariable("appName", APP_NAME);
        ctx.setVariable("verificationLink", link);

        sendHtmlEmail(to, APP_NAME + " — Verify your email", "account-verification-email", ctx);
    }

    public void sendWelcomeEmail(String to, String name) {
        String homeLink = baseUrl + CONTEXT_PATH + HOME_PATH;

        Context ctx = new Context();
        ctx.setVariable("name", name);
        ctx.setVariable("appName", APP_NAME);
        ctx.setVariable("homeLink", homeLink);

        sendHtmlEmail(to, "Welcome to " + APP_NAME, "welcome-email", ctx);
    }

    public void sendResetPasswordEmail(String to, String name, String token, int expirationMinutes) {
        String link = baseUrl + CONTEXT_PATH + RESET_PATH + "?token=" + token;

        Context ctx = new Context();
        ctx.setVariable("name", name);
        ctx.setVariable("appName", APP_NAME);
        ctx.setVariable("resetLink", link);
        ctx.setVariable("expirationMinutes", expirationMinutes);

        sendHtmlEmail(to, APP_NAME + " — Reset your password", "password-reset-email", ctx);
    }
}
