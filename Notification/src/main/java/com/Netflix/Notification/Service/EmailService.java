package com.Netflix.Notification.Service;

import com.Netflix.Notification.DTO.PasswordResetRequestedEvent;
import com.Netflix.Notification.DTO.PaymentFailedEvent;
import com.Netflix.Notification.DTO.PaymentSucceededEvent;
import com.Netflix.Notification.DTO.UserRegisteredEvent;

public interface EmailService {
    void sendWelcomeEmail(UserRegisteredEvent event);
    void sendPasswordResetEmail(PasswordResetRequestedEvent event);
    void sendPaymentSuccessEmail(PaymentSucceededEvent event);
    void sendPaymentFailureEmail(PaymentFailedEvent event);
}