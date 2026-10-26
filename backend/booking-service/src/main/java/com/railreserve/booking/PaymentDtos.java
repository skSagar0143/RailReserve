package com.railreserve.booking;

import java.math.BigDecimal;
import java.time.Instant;

public final class PaymentDtos {
    private PaymentDtos() {}

    public record CheckoutResponse(String bookingId, String bookingReference, String paymentLinkId,
                                   String checkoutUrl, BigDecimal amount, String currency, String status) {}
    public record PaymentStatus(String bookingId, String bookingReference, String bookingStatus,
                                String paymentStatus, String paymentLinkId, String checkoutUrl,
                                BigDecimal amount, String currency, Instant paidAt) {}
    public record ReconciliationItem(String paymentId, String bookingId, String bookingReference,
                                     String customerId, String provider, String providerLinkId,
                                     String providerPaymentId, String status, BigDecimal amount,
                                     String currency, Instant createdAt, Instant updatedAt) {}
    public record RefundRequest(String reason) {}
    public record RefundResponse(String refundId, String paymentId, String status,
                                 String providerRefundId, String message, Instant createdAt) {}
}