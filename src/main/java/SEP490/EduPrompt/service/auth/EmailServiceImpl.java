package SEP490.EduPrompt.service.auth;

import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.io.IOException;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements EmailService {

    private static final String APP_NAME = "EduPrompt";
    private static final String CONTEXT_PATH = "/BE";
    //TODO: update frontend url
    private static final String FRONTEND_RESET_PASSWORD_URL = "http://localhost:3000/reset-password";
    private static final String HOME_PAGE = "http://localhost:3000"; //frontend url here :v
    private static final String VERIFY_PATH = "/api/auth/verify-email";
    private static final String RESET_PATH = "/api/auth/reset-password";
    private static final String fromName = "Trí Nguyễn"; // dont ever change this
    private final SendGrid sendGrid;
    private final SpringTemplateEngine templateEngine;
    @Value("${spring.sendgrid.from.email}")
    private String fromEmail;
    @Value("${app.base-url}")
    private String baseUrl;

    @Override
    public void sendVerificationEmail(String toEmail, String name, String token) {
        log.info("Sending verification email to: {}", toEmail);

        String link = baseUrl + CONTEXT_PATH + VERIFY_PATH + "?token=" + token;

        Context context = new Context();
        context.setVariable("appName", APP_NAME);
        context.setVariable("name", name);
        context.setVariable("verificationLink", link);

        String htmlContent = templateEngine.process("account-verification-email", context);
        sendEmail(toEmail, "Verify your email address", htmlContent);
    }

    @Override
    public void sendPasswordResetEmail(String toEmail, String name, String token, int expirationMinutes) {
        log.info("Sending password reset email to: {}", toEmail);

        String link = FRONTEND_RESET_PASSWORD_URL + "?token=" + token;
        Context context = new Context();
        context.setVariable("appName", APP_NAME);
        context.setVariable("name", name);
        context.setVariable("resetLink", link);
        context.setVariable("expirationMinutes", expirationMinutes);

        String htmlContent = templateEngine.process("password-reset-email", context);
        sendEmail(toEmail, "Reset your password", htmlContent);
    }

    @Override
    public void sendWelcomeEmail(String toEmail, String name) {
        log.info("Sending welcome email to: {}", toEmail);

        Context context = new Context();
        context.setVariable("appName", APP_NAME);
        context.setVariable("name", name);
        context.setVariable("homeLink", HOME_PAGE);

        String htmlContent = templateEngine.process("welcome-email", context);
        sendEmail(toEmail, "Welcome to " + APP_NAME, htmlContent);
    }

    private void sendEmail(String toEmail, String subject, String htmlContent) {
        try {
            Email from = new Email(fromEmail, fromName);
            Email to = new Email(toEmail);
            Content content = new Content("text/html", htmlContent);
            Mail mail = new Mail(from, subject, to, content);

            Request request = new Request();
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());

            Response response = sendGrid.api(request);

            if (response.getStatusCode() >= 200 && response.getStatusCode() < 300) {
                log.info("Email sent successfully to: {} with status code: {}", toEmail, response.getStatusCode());
            } else {
                log.error("Failed to send email to: {}. Status code: {}, Body: {}",
                        toEmail, response.getStatusCode(), response.getBody());
            }
        } catch (IOException e) {
            log.error("Error sending email to: {}", toEmail, e);
            throw new RuntimeException("Failed to send email", e);
        }
    }
}