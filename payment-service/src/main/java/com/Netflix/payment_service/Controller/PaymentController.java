package com.Netflix.payment_service.Controller;

import com.Netflix.payment_service.DTO.CheckoutRequest;
import com.Netflix.payment_service.DTO.CheckoutResponse;
import com.Netflix.payment_service.Service.PaymentService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/checkout-session")
    public ResponseEntity<CheckoutResponse> createCheckoutSession(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Email") String userEmail,
            @RequestBody CheckoutRequest request) throws StripeException {

        CheckoutResponse response = paymentService.createCheckoutSession(userId, userEmail, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Webhook endpoint called by Stripe.
     * Accessible publicly via Gateway (no JWT filter required).
     */
    @PostMapping("/webhook")
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String rawPayload,
            @RequestHeader("Stripe-Signature") String sigHeader) {

        try {
            paymentService.handleStripeWebhook(rawPayload, sigHeader);
            return ResponseEntity.ok("Webhook processed successfully");
        } catch (SignatureVerificationException e) {
            log.error("Invalid Stripe signature: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
        } catch (Exception e) {
            log.error("Error processing webhook: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Webhook handling error");
        }
    }
}
