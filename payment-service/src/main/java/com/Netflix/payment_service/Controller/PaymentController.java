package com.Netflix.payment_service.Controller;

import com.Netflix.payment_service.DTO.CheckoutRequest;
import com.Netflix.payment_service.DTO.CheckoutResponse;
import com.Netflix.payment_service.DTO.SubscriptionStatusResponse;
import com.Netflix.payment_service.Service.PaymentService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * Creates a Stripe checkout session for authenticated users passing X-User-Id from Gateway.
     */
    @PostMapping("/checkout-session")
    public ResponseEntity<CheckoutResponse> createCheckoutSession(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Email", required = false) String userEmail,
            @Valid @RequestBody CheckoutRequest request) throws StripeException {

        CheckoutResponse response = paymentService.createCheckoutSession(userId, userEmail, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Stripe Webhook endpoint. Publicly accessible (Gateway bypasses JWT validation for this route).
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

    /**
     * Checks subscription status for the header-identified user.
     */
    @GetMapping("/status")
    public ResponseEntity<SubscriptionStatusResponse> getSubscriptionStatus(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(paymentService.getSubscriptionStatus(userId));
    }

    /**
     * Cancels an active subscription for the header-identified user.
     */
    @PostMapping("/cancel")
    public ResponseEntity<Map<String, Object>> cancelSubscription(
            @RequestHeader("X-User-Id") String userId) throws StripeException {

        paymentService.cancelSubscription(userId);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Subscription cancelled successfully."
        ));
    }
}