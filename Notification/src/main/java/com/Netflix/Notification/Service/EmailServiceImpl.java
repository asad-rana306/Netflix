package com.Netflix.Notification.Service;

import com.Netflix.Notification.DTO.PasswordResetRequestedEvent;
import com.Netflix.Notification.DTO.PaymentFailedEvent;
import com.Netflix.Notification.DTO.PaymentSucceededEvent;
import com.Netflix.Notification.DTO.UserRegisteredEvent;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;

    @Value("${app.mail.from-address:no-reply@netflix.com}")
    private String fromAddress;

    @Value("${app.mail.from-name:Netflix}")
    private String fromName;

    @Override
    public void sendWelcomeEmail(UserRegisteredEvent event) {
        Context context = new Context();
        context.setVariables(Map.of(
                "firstName", event.getFirstName() != null ? event.getFirstName() : "Member",
                "planType", event.getPlanType() != null ? event.getPlanType() : "Standard"
        ));
        sendHtmlEmail(event.getEmail(), "Welcome to Netflix!", "welcome-email", context);
    }

    @Override
    public void sendPasswordResetEmail(PasswordResetRequestedEvent event) {
        Context context = new Context();
        context.setVariables(Map.of(
                "firstName", event.getFirstName() != null ? event.getFirstName() : "Member",
                "resetUrl", event.getResetUrl() != null ? event.getResetUrl() : "#"
        ));
        sendHtmlEmail(event.getEmail(), "Complete your password reset request", "password-reset-email", context);
    }

    @Override
    public void sendPaymentSuccessEmail(PaymentSucceededEvent event) {
        Context context = new Context();
        context.setVariables(Map.of(
                "userName", event.getUserName() != null ? event.getUserName() : "Valued Member",
                "amount", event.getAmount() != null ? event.getAmount() : "0.00",
                "currency", event.getCurrency() != null ? event.getCurrency() : "USD",
                "transactionId", event.getTransactionId() != null ? event.getTransactionId() : "N/A",
                "subscriptionPlan", event.getSubscriptionPlan() != null ? event.getSubscriptionPlan() : "Monthly Subscription",
                "invoicePdfUrl", event.getInvoicePdfUrl() != null ? event.getInvoicePdfUrl() : "#"
        ));
        sendHtmlEmail(event.getUserEmail(), "Your Netflix Payment Receipt", "payment-success-email", context);
    }

    @Override
    public void sendPaymentFailureEmail(PaymentFailedEvent event) {
        Context context = new Context();
        context.setVariables(Map.of(
                "userName", event.getUserName() != null ? event.getUserName() : "Valued Member",
                "amount", event.getAmount() != null ? event.getAmount() : "0.00",
                "currency", event.getCurrency() != null ? event.getCurrency() : "USD",
                "failureReason", event.getFailureReason() != null ? event.getFailureReason() : "Payment processing issue",
                "retryPaymentUrl", event.getRetryPaymentUrl() != null ? event.getRetryPaymentUrl() : "#"
        ));
        sendHtmlEmail(event.getUserEmail(), "ACTION REQUIRED: Update your payment details", "payment-failure-email", context);
    }

    private void sendHtmlEmail(String to, String subject, String templateName, Context context) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message,
                    MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                    StandardCharsets.UTF_8.name()
            );

            String htmlContent = templateEngine.process(templateName, context);

            helper.setFrom(fromAddress, fromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Successfully sent email [{}] to: {}", subject, to);
        } catch (Exception e) {
            log.error("Failed to send email [{}] to: {}", subject, to, e);
            throw new RuntimeException("Email delivery failed for " + to, e);
        }
    }
}