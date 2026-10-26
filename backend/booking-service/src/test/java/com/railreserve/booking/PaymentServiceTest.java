package com.railreserve.booking;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {
    @Mock JdbcTemplate jdbc;
    private PaymentService service;

    @BeforeEach
    void setUp() {
        service = new PaymentService(jdbc, new ObjectMapper(), "rzp_test_key", "key-secret",
            "webhook-secret", "http://localhost:5173/payment-result", "INR", true);
    }

    @Test
    void rejectsMissingOrInvalidWebhookSignatureBeforeDatabaseWork() {
        assertThrows(AccessDeniedException.class, () -> service.handleWebhook("{\"event\":\"payment_link.paid\"}", null));
        assertThrows(AccessDeniedException.class, () -> service.handleWebhook("{\"event\":\"payment_link.paid\"}", "00"));
        verifyNoInteractions(jdbc);
    }

    @Test
    void recordsSignedUnrelatedEventsButDoesNotApplyPaymentSideEffects() throws Exception {
        String body = "{\"event\":\"payment_link.cancelled\"}";
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("webhook-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        service.handleWebhook(body, signature);
        org.mockito.Mockito.verify(jdbc).update(
            org.mockito.ArgumentMatchers.eq("INSERT IGNORE INTO payment_webhook_events(event_id,event_type) VALUES(?,?)"),
            org.mockito.ArgumentMatchers.eq("body-sha256-" + java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8)))),
            org.mockito.ArgumentMatchers.eq("payment_link.cancelled"));
        org.mockito.Mockito.verifyNoMoreInteractions(jdbc);
    }

    @Test
    void ignoresDuplicateProviderEventIdBeforeApplyingPaymentSideEffects() throws Exception {
        String body = "{\"event\":\"payment_link.paid\"}";
        org.mockito.Mockito.doReturn(0).when(jdbc).update(
            org.mockito.ArgumentMatchers.eq("INSERT IGNORE INTO payment_webhook_events(event_id,event_type) VALUES(?,?)"),
            org.mockito.ArgumentMatchers.eq("evt_duplicate"), org.mockito.ArgumentMatchers.eq("payment_link.paid"));

        service.handleWebhook(body, signature(body), "evt_duplicate");

        org.mockito.Mockito.verify(jdbc).update(
            org.mockito.ArgumentMatchers.eq("INSERT IGNORE INTO payment_webhook_events(event_id,event_type) VALUES(?,?)"),
            org.mockito.ArgumentMatchers.eq("evt_duplicate"), org.mockito.ArgumentMatchers.eq("payment_link.paid"));
        org.mockito.Mockito.verify(jdbc, org.mockito.Mockito.never()).queryForList(
            org.mockito.ArgumentMatchers.contains("FROM payments p JOIN bookings b"), (Object[]) org.mockito.ArgumentMatchers.any(Object[].class));
        org.mockito.Mockito.verify(jdbc, org.mockito.Mockito.times(1)).update(
            org.mockito.ArgumentMatchers.eq("INSERT IGNORE INTO payment_webhook_events(event_id,event_type) VALUES(?,?)"),
            org.mockito.ArgumentMatchers.eq("evt_duplicate"), org.mockito.ArgumentMatchers.eq("payment_link.paid"));
    }

    @Test
    void deduplicatesExactWebhookRetriesWhenProviderEventIdHeaderIsMissing() throws Exception {
        String body = "{\"event\":\"payment_link.paid\"}";
        String fingerprint = "body-sha256-" + java.util.HexFormat.of().formatHex(
            java.security.MessageDigest.getInstance("SHA-256").digest(body.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        org.mockito.Mockito.doReturn(1, 0).when(jdbc).update(
            org.mockito.ArgumentMatchers.eq("INSERT IGNORE INTO payment_webhook_events(event_id,event_type) VALUES(?,?)"),
            org.mockito.ArgumentMatchers.eq(fingerprint), org.mockito.ArgumentMatchers.eq("payment_link.paid"));

        service.handleWebhook(body, signature(body));
        service.handleWebhook(body, signature(body));

        org.mockito.Mockito.verify(jdbc, org.mockito.Mockito.times(2)).update(
            org.mockito.ArgumentMatchers.eq("INSERT IGNORE INTO payment_webhook_events(event_id,event_type) VALUES(?,?)"),
            org.mockito.ArgumentMatchers.eq(fingerprint), org.mockito.ArgumentMatchers.eq("payment_link.paid"));
        org.mockito.Mockito.verify(jdbc, org.mockito.Mockito.never()).queryForList(
            org.mockito.ArgumentMatchers.contains("FROM payments p JOIN bookings b"), (Object[]) org.mockito.ArgumentMatchers.any(Object[].class));
    }

    @Test
    void rejectsProviderWebhookEventIdLongerThanDatabaseColumn() throws Exception {
        String body = "{\"event\":\"payment_link.paid\"}";
        assertThrows(IllegalArgumentException.class, () -> service.handleWebhook(body, signature(body), "e".repeat(129)));
        verifyNoInteractions(jdbc);
    }

    @Test
    void refusesCheckoutWhenPaymentIsDisabled() {
        PaymentService disabled = new PaymentService(jdbc, new ObjectMapper(), "", "", "",
            "http://localhost:5173/payment-result", "INR", false);
        assertThrows(IllegalStateException.class, () -> disabled.createCheckout(java.util.UUID.randomUUID(), java.util.UUID.randomUUID()));
        verifyNoInteractions(jdbc);
    }
    @Test
    void confirmsBookingOnlyAfterSignedWebhookMatchesAmountAndCurrency() throws Exception {
        java.util.UUID bookingId = java.util.UUID.randomUUID();
        java.util.UUID customerId = java.util.UUID.randomUUID();
        java.util.UUID paymentId = java.util.UUID.randomUUID();
        java.util.Map<String,Object> row = new java.util.HashMap<>();
        row.put("id", uuidBytes(paymentId));
        row.put("booking_id", uuidBytes(bookingId));
        row.put("status", "CREATED");
        row.put("amount", new java.math.BigDecimal("25.00"));
        row.put("currency", "INR");
        row.put("booking_status", "PENDING_PAYMENT");
        row.put("booking_reference", "RR-TEST123");
        row.put("customer_id", uuidBytes(customerId));
        row.put("hold_expires_at", java.sql.Timestamp.from(java.time.Instant.now().plusSeconds(300)));
        org.mockito.Mockito.doReturn(java.util.List.of(row)).when(jdbc).queryForList(
            org.mockito.ArgumentMatchers.contains("FROM payments p JOIN bookings b"), org.mockito.ArgumentMatchers.eq("plink_test"));
        String body = "{\"event\":\"payment_link.paid\",\"payload\":{\"payment_link\":{\"entity\":{\"id\":\"plink_test\",\"amount_paid\":2500,\"currency\":\"INR\"}},\"payment\":{\"entity\":{\"id\":\"pay_test\"}}}}";
        service.handleWebhook(body, signature(body));
        org.mockito.Mockito.verify(jdbc).update(org.mockito.ArgumentMatchers.contains("UPDATE payments SET status='PAID'"), (Object[]) org.mockito.ArgumentMatchers.any(Object[].class));
        org.mockito.Mockito.verify(jdbc).update(org.mockito.ArgumentMatchers.contains("UPDATE bookings SET status='CONFIRMED'"), (Object[]) org.mockito.ArgumentMatchers.any(Object[].class));
        org.mockito.Mockito.verify(jdbc).update(org.mockito.ArgumentMatchers.contains("INSERT INTO outbox_events"), (Object[]) org.mockito.ArgumentMatchers.any(Object[].class));
    }

    @Test
    void doesNotConfirmBookingWhenSignedWebhookAmountDoesNotMatch() throws Exception {
        java.util.UUID bookingId = java.util.UUID.randomUUID();
        java.util.Map<String,Object> row = new java.util.HashMap<>();
        row.put("id", uuidBytes(java.util.UUID.randomUUID()));
        row.put("booking_id", uuidBytes(bookingId));
        row.put("status", "CREATED");
        row.put("amount", new java.math.BigDecimal("25.00"));
        row.put("currency", "INR");
        row.put("booking_status", "PENDING_PAYMENT");
        row.put("booking_reference", "RR-TEST456");
        row.put("customer_id", uuidBytes(java.util.UUID.randomUUID()));
        row.put("hold_expires_at", java.sql.Timestamp.from(java.time.Instant.now().plusSeconds(300)));
        org.mockito.Mockito.doReturn(java.util.List.of(row)).when(jdbc).queryForList(
            org.mockito.ArgumentMatchers.contains("FROM payments p JOIN bookings b"), org.mockito.ArgumentMatchers.eq("plink_bad"));
        String body = "{\"event\":\"payment_link.paid\",\"payload\":{\"payment_link\":{\"entity\":{\"id\":\"plink_bad\",\"amount_paid\":1000,\"currency\":\"INR\"}}}}";
        service.handleWebhook(body, signature(body));
        org.mockito.Mockito.verify(jdbc).update(org.mockito.ArgumentMatchers.contains("status='REVIEW_REQUIRED'"), (Object[]) org.mockito.ArgumentMatchers.any(Object[].class));
        org.mockito.Mockito.verify(jdbc, org.mockito.Mockito.never()).update(org.mockito.ArgumentMatchers.contains("UPDATE bookings SET status='CONFIRMED'"), (Object[]) org.mockito.ArgumentMatchers.any(Object[].class));
    }

    @Test
    void marksPaymentAfterExpiredSeatHoldForReviewInsteadOfConfirmingBooking() throws Exception {
        java.util.UUID bookingId = java.util.UUID.randomUUID();
        java.util.Map<String,Object> row = new java.util.HashMap<>();
        row.put("id", uuidBytes(java.util.UUID.randomUUID()));
        row.put("booking_id", uuidBytes(bookingId));
        row.put("status", "CREATED");
        row.put("amount", new java.math.BigDecimal("25.00"));
        row.put("currency", "INR");
        row.put("booking_status", "EXPIRED");
        row.put("booking_reference", "RR-LATE123");
        row.put("customer_id", uuidBytes(java.util.UUID.randomUUID()));
        row.put("hold_expires_at", java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(60)));
        org.mockito.Mockito.doReturn(java.util.List.of(row)).when(jdbc).queryForList(
            org.mockito.ArgumentMatchers.contains("FROM payments p JOIN bookings b"),
            org.mockito.ArgumentMatchers.eq("plink_late"));
        String body = "{\"event\":\"payment_link.paid\",\"payload\":{\"payment_link\":{\"entity\":{\"id\":\"plink_late\",\"amount_paid\":2500,\"currency\":\"INR\"}},\"payment\":{\"entity\":{\"id\":\"pay_late\"}}}}";

        service.handleWebhook(body, signature(body));

        org.mockito.Mockito.verify(jdbc).update(
            org.mockito.ArgumentMatchers.contains("status='PAID_AFTER_EXPIRY'"),
            (Object[]) org.mockito.ArgumentMatchers.any(Object[].class));
        org.mockito.Mockito.verify(jdbc, org.mockito.Mockito.never()).update(
            org.mockito.ArgumentMatchers.contains("UPDATE bookings SET status='CONFIRMED'"),
            (Object[]) org.mockito.ArgumentMatchers.any(Object[].class));
        org.mockito.Mockito.verify(jdbc, org.mockito.Mockito.never()).update(
            org.mockito.ArgumentMatchers.contains("INSERT INTO outbox_events"),
            (Object[]) org.mockito.ArgumentMatchers.any(Object[].class));
    }

    @Test
    void doesNotConfirmBookingWhenSignedWebhookCurrencyDoesNotMatch() throws Exception {
        java.util.UUID bookingId = java.util.UUID.randomUUID();
        java.util.Map<String,Object> row = new java.util.HashMap<>();
        row.put("id", uuidBytes(java.util.UUID.randomUUID()));
        row.put("booking_id", uuidBytes(bookingId));
        row.put("status", "CREATED");
        row.put("amount", new java.math.BigDecimal("25.00"));
        row.put("currency", "INR");
        row.put("booking_status", "PENDING_PAYMENT");
        row.put("booking_reference", "RR-CURRENCY1");
        row.put("customer_id", uuidBytes(java.util.UUID.randomUUID()));
        row.put("hold_expires_at", java.sql.Timestamp.from(java.time.Instant.now().plusSeconds(300)));
        org.mockito.Mockito.doReturn(java.util.List.of(row)).when(jdbc).queryForList(
            org.mockito.ArgumentMatchers.contains("FROM payments p JOIN bookings b"),
            org.mockito.ArgumentMatchers.eq("plink_currency"));
        String body = "{\"event\":\"payment_link.paid\",\"payload\":{\"payment_link\":{\"entity\":{\"id\":\"plink_currency\",\"amount_paid\":2500,\"currency\":\"USD\"}}}}";

        service.handleWebhook(body, signature(body));

        org.mockito.Mockito.verify(jdbc).update(
            org.mockito.ArgumentMatchers.contains("status='REVIEW_REQUIRED'"),
            (Object[]) org.mockito.ArgumentMatchers.any(Object[].class));
        org.mockito.Mockito.verify(jdbc, org.mockito.Mockito.never()).update(
            org.mockito.ArgumentMatchers.contains("UPDATE bookings SET status='CONFIRMED'"),
            (Object[]) org.mockito.ArgumentMatchers.any(Object[].class));
    }



    @Test
    void rejectsRefundForUnknownPaymentWithoutCreatingRefundRecord() {
        java.util.UUID paymentId = java.util.UUID.randomUUID();
        org.mockito.Mockito.doReturn(java.util.List.of()).when(jdbc).queryForList(
            org.mockito.ArgumentMatchers.contains("SELECT id,provider,provider_payment_id,status FROM payments WHERE id=? FOR UPDATE"),
            (Object[]) org.mockito.ArgumentMatchers.any(Object[].class));

        assertThrows(BookingConflictException.class,
            () -> service.prepareRefund(paymentId, "Customer cancellation", "admin-user"));

        org.mockito.Mockito.verify(jdbc, org.mockito.Mockito.never()).update(
            org.mockito.ArgumentMatchers.contains("INSERT INTO payment_refunds"),
            (Object[]) org.mockito.ArgumentMatchers.any(Object[].class));
    }

    @Test
    void rejectsBlankRefundReasonBeforeDatabaseWork() {
        assertThrows(IllegalArgumentException.class,
            () -> service.prepareRefund(java.util.UUID.randomUUID(), "  ", "admin"));
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsRefundReasonLongerThanProviderAuditLimitBeforeDatabaseWork() {
        assertThrows(IllegalArgumentException.class,
            () -> service.prepareRefund(java.util.UUID.randomUUID(), "x".repeat(501), "admin"));
        verifyNoInteractions(jdbc);
    }

    @Test
    void existingRefundRequestIsReturnedWithoutCreatingAnotherRefund() {
        java.util.UUID paymentId = java.util.UUID.randomUUID();
        java.util.UUID refundId = java.util.UUID.randomUUID();
        java.util.Map<String,Object> payment = new java.util.HashMap<>();
        payment.put("id", uuidBytes(paymentId));
        payment.put("provider", "RAZORPAY");
        payment.put("provider_payment_id", "pay_existing");
        payment.put("status", "REFUND_REVIEW_REQUIRED");
        java.util.Map<String,Object> refund = new java.util.HashMap<>();
        refund.put("id", uuidBytes(refundId));
        refund.put("status", "REFUND_REVIEW_REQUIRED");
        refund.put("provider_refund_id", null);
        refund.put("reason", "Customer request");
        refund.put("created_at", java.sql.Timestamp.from(java.time.Instant.now()));
        org.mockito.Mockito.doReturn(java.util.List.of(payment)).when(jdbc).queryForList(
            org.mockito.ArgumentMatchers.contains("SELECT id,provider,provider_payment_id,status FROM payments WHERE id=? FOR UPDATE"),
            (Object[]) org.mockito.ArgumentMatchers.any(Object[].class));
        org.mockito.Mockito.doReturn(java.util.List.of(refund)).when(jdbc).queryForList(
            org.mockito.ArgumentMatchers.contains("SELECT id,status,provider_refund_id,reason,created_at FROM payment_refunds WHERE payment_id=?"),
            (Object[]) org.mockito.ArgumentMatchers.any(Object[].class));

        PaymentService.PreparedRefund result = service.prepareRefund(paymentId, "A different retry reason", "admin");

        org.junit.jupiter.api.Assertions.assertEquals(refundId, result.refundId());
        org.junit.jupiter.api.Assertions.assertFalse(result.newlyCreated());
        org.junit.jupiter.api.Assertions.assertEquals("REFUND_REVIEW_REQUIRED", result.status());
        org.mockito.Mockito.verify(jdbc, org.mockito.Mockito.never()).update(
            org.mockito.ArgumentMatchers.contains("INSERT INTO payment_refunds"),
            (Object[]) org.mockito.ArgumentMatchers.any(Object[].class));
    }

    @Test
    void rejectsRefundForUnreviewedPaymentWithoutCreatingRefundIntent() {
        java.util.UUID paymentId = java.util.UUID.randomUUID();
        java.util.Map<String,Object> payment = new java.util.HashMap<>();
        payment.put("id", uuidBytes(paymentId));
        payment.put("provider", "RAZORPAY");
        payment.put("provider_payment_id", "pay_paid");
        payment.put("status", "PAID");
        org.mockito.Mockito.doReturn(java.util.List.of(payment)).when(jdbc).queryForList(
            org.mockito.ArgumentMatchers.contains("SELECT id,provider,provider_payment_id,status FROM payments WHERE id=? FOR UPDATE"),
            (Object[]) org.mockito.ArgumentMatchers.any(Object[].class));
        org.mockito.Mockito.doReturn(java.util.List.of()).when(jdbc).queryForList(
            org.mockito.ArgumentMatchers.contains("SELECT id,status,provider_refund_id,reason,created_at FROM payment_refunds WHERE payment_id=?"),
            (Object[]) org.mockito.ArgumentMatchers.any(Object[].class));

        assertThrows(BookingConflictException.class,
            () -> service.prepareRefund(paymentId, "Customer cancellation", "admin"));

        org.mockito.Mockito.verify(jdbc, org.mockito.Mockito.never()).update(
            org.mockito.ArgumentMatchers.contains("INSERT INTO payment_refunds"),
            (Object[]) org.mockito.ArgumentMatchers.any(Object[].class));
    }

    private static String signature(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("webhook-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    private static byte[] uuidBytes(java.util.UUID uuid) {
        return java.nio.ByteBuffer.allocate(16).putLong(uuid.getMostSignificantBits()).putLong(uuid.getLeastSignificantBits()).array();
    }


    @Test
    void rejectsMalformedSignedWebhookPayloadWithoutDatabaseMutation() throws Exception {
        String body = "{not-json";
        assertThrows(IllegalArgumentException.class, () -> service.handleWebhook(body, signature(body)));
        verifyNoInteractions(jdbc);
    }

    @Test
    void ignoresSignedPaymentEventsWithoutPaymentLinkPayload() throws Exception {
        String body = "{\"event\":\"payment_link.paid\",\"payload\":{}}";
        service.handleWebhook(body, signature(body));
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsWebhookWhenSecretIsNotConfiguredBeforeDatabaseWork() {
        PaymentService unconfigured = new PaymentService(jdbc, new ObjectMapper(), "rzp_test_key", "key-secret",
            "", "http://localhost:5173/payment-result", "INR", true);
        assertThrows(IllegalStateException.class, () -> unconfigured.handleWebhook("{}", "00"));
        verifyNoInteractions(jdbc);
    }

    @Test
    void boundsReconciliationQueueLimitToSafeRange() {
        org.mockito.Mockito.doReturn(java.util.List.of()).when(jdbc).query(
            org.mockito.ArgumentMatchers.contains("WHERE p.status IN ('REVIEW_REQUIRED','PAID_AFTER_EXPIRY','REFUND_REQUESTED','REFUND_REVIEW_REQUIRED','REFUND_PENDING')"),
            org.mockito.ArgumentMatchers.any(org.springframework.jdbc.core.RowMapper.class),
            org.mockito.ArgumentMatchers.eq(100));
        service.reconciliationQueue(10000);
        org.mockito.Mockito.verify(jdbc).query(
            org.mockito.ArgumentMatchers.contains("WHERE p.status IN ('REVIEW_REQUIRED','PAID_AFTER_EXPIRY','REFUND_REQUESTED','REFUND_REVIEW_REQUIRED','REFUND_PENDING')"),
            org.mockito.ArgumentMatchers.any(org.springframework.jdbc.core.RowMapper.class),
            org.mockito.ArgumentMatchers.eq(100));
    }

    @Test
    void enforcesMinimumReconciliationQueueLimit() {
        org.mockito.Mockito.doReturn(java.util.List.of()).when(jdbc).query(
            org.mockito.ArgumentMatchers.contains("WHERE p.status IN ('REVIEW_REQUIRED','PAID_AFTER_EXPIRY','REFUND_REQUESTED','REFUND_REVIEW_REQUIRED','REFUND_PENDING')"),
            org.mockito.ArgumentMatchers.any(org.springframework.jdbc.core.RowMapper.class),
            org.mockito.ArgumentMatchers.eq(1));
        service.reconciliationQueue(0);
        org.mockito.Mockito.verify(jdbc).query(
            org.mockito.ArgumentMatchers.contains("WHERE p.status IN ('REVIEW_REQUIRED','PAID_AFTER_EXPIRY','REFUND_REQUESTED','REFUND_REVIEW_REQUIRED','REFUND_PENDING')"),
            org.mockito.ArgumentMatchers.any(org.springframework.jdbc.core.RowMapper.class),
            org.mockito.ArgumentMatchers.eq(1));
    }


    @Test
    void refusesCheckoutWhenAnyRequiredProviderCredentialIsMissing() {
        PaymentService missingWebhook = new PaymentService(jdbc, new ObjectMapper(), "rzp_test_key", "key-secret",
            "", "http://localhost:5173/payment-result", "INR", true);
        assertThrows(IllegalStateException.class, () ->
            missingWebhook.createCheckout(java.util.UUID.randomUUID(), java.util.UUID.randomUUID()));
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsMissingIdentityForPaymentStatusBeforeDatabaseWork() {
        assertThrows(IllegalArgumentException.class, () -> service.status(null, java.util.UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> service.status(java.util.UUID.randomUUID(), null));
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsMissingIdentityForCheckoutBeforeDatabaseWork() {
        assertThrows(IllegalArgumentException.class, () -> service.createCheckout(null, java.util.UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> service.createCheckout(java.util.UUID.randomUUID(), null));
        verifyNoInteractions(jdbc);
    }


    @Test
    void checkoutRejectsBookingOwnedByAnotherCustomerBeforePaymentLookup() {
        java.util.UUID bookingId = java.util.UUID.randomUUID();
        java.util.UUID ownerId = java.util.UUID.randomUUID();
        java.util.UUID callerId = java.util.UUID.randomUUID();
        java.util.Map<String,Object> booking = new java.util.HashMap<>();
        booking.put("booking_reference", "RR-OWNER123");
        booking.put("status", "PENDING_PAYMENT");
        booking.put("total_amount", new java.math.BigDecimal("25.00"));
        booking.put("hold_expires_at", java.sql.Timestamp.from(java.time.Instant.now().plusSeconds(300)));
        booking.put("customer_id", uuidBytes(ownerId));
        org.mockito.Mockito.doReturn(java.util.List.of(booking)).when(jdbc).queryForList(
            org.mockito.ArgumentMatchers.contains("SELECT booking_reference,status,total_amount,hold_expires_at,customer_id FROM bookings WHERE id=? FOR UPDATE"),
            (Object[]) org.mockito.ArgumentMatchers.any(Object[].class));

        assertThrows(AccessDeniedException.class, () -> service.createCheckout(callerId, bookingId));

        org.mockito.Mockito.verify(jdbc, org.mockito.Mockito.never()).queryForList(
            org.mockito.ArgumentMatchers.contains("SELECT provider_link_id,payment_url,amount,currency,status FROM payments WHERE booking_id=?"),
            (Object[]) org.mockito.ArgumentMatchers.any(Object[].class));
    }

    @Test
    void checkoutRejectsExpiredSeatHoldBeforePaymentLookup() {
        java.util.UUID bookingId = java.util.UUID.randomUUID();
        java.util.UUID customerId = java.util.UUID.randomUUID();
        java.util.Map<String,Object> booking = new java.util.HashMap<>();
        booking.put("booking_reference", "RR-EXPIRED1");
        booking.put("status", "PENDING_PAYMENT");
        booking.put("total_amount", new java.math.BigDecimal("25.00"));
        booking.put("hold_expires_at", java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(60)));
        booking.put("customer_id", uuidBytes(customerId));
        org.mockito.Mockito.doReturn(java.util.List.of(booking)).when(jdbc).queryForList(
            org.mockito.ArgumentMatchers.contains("SELECT booking_reference,status,total_amount,hold_expires_at,customer_id FROM bookings WHERE id=? FOR UPDATE"),
            (Object[]) org.mockito.ArgumentMatchers.any(Object[].class));

        assertThrows(BookingConflictException.class, () -> service.createCheckout(customerId, bookingId));

        org.mockito.Mockito.verify(jdbc, org.mockito.Mockito.never()).queryForList(
            org.mockito.ArgumentMatchers.contains("SELECT provider_link_id,payment_url,amount,currency,status FROM payments WHERE booking_id=?"),
            (Object[]) org.mockito.ArgumentMatchers.any(Object[].class));
    }

    @Test
    void paymentStatusRejectsBookingOwnedByAnotherCustomer() {
        java.util.UUID bookingId = java.util.UUID.randomUUID();
        java.util.UUID ownerId = java.util.UUID.randomUUID();
        java.util.UUID callerId = java.util.UUID.randomUUID();
        java.util.Map<String,Object> row = new java.util.HashMap<>();
        row.put("booking_reference", "RR-OWNER456");
        row.put("booking_status", "PENDING_PAYMENT");
        row.put("customer_id", uuidBytes(ownerId));
        org.mockito.Mockito.doReturn(java.util.List.of(row)).when(jdbc).queryForList(
            org.mockito.ArgumentMatchers.contains("SELECT b.booking_reference,b.status AS booking_status,b.customer_id,b.total_amount,p.status AS payment_status"),
            (Object[]) org.mockito.ArgumentMatchers.any(Object[].class));

        assertThrows(AccessDeniedException.class, () -> service.status(callerId, bookingId));
    }

}
