package SEP490.EduPrompt.service.auth;

public interface EmailService {
    void sendVerificationEmail(String toEmail, String name, String verificationLink);

    void sendPasswordResetEmail(String toEmail, String name, String resetLink, int expirationMinutes);

    void sendWelcomeEmail(String toEmail, String name);
}