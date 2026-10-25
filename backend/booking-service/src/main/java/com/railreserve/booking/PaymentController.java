package com.railreserve.booking;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {
    private final PaymentService payments;
    public PaymentController(PaymentService payments) { this.payments=payments; }

    @PostMapping("/bookings/{bookingId}/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentDtos.CheckoutResponse checkout(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID bookingId) {
        return payments.createCheckout(UUID.fromString(jwt.getSubject()),bookingId);
    }

    @GetMapping("/bookings/{bookingId}")
    public PaymentDtos.PaymentStatus status(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID bookingId) {
        return payments.status(UUID.fromString(jwt.getSubject()),bookingId);
    }

    @PostMapping("/webhook")
    @ResponseStatus(HttpStatus.OK)
    public Map<String,String> webhook(@RequestBody String rawBody,@RequestHeader(value="X-Razorpay-Signature",required=false) String signature) {
        payments.handleWebhook(rawBody,signature);
        return Map.of("status","accepted");
    }
}