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
}