package SEP490.EduPrompt.service.auth;

public interface EmailService {
    void sendVerificationEmail(String to, String name, String token);

    void sendWelcomeEmail(String to, String name);

    void sendResetPasswordEmail(String to, String name, String token, int expirationMinutes);
}
