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
    void acceptsSignedUnrelatedEventsWithoutDatabaseMutation() throws Exception {
        String body = "{\"event\":\"payment_link.cancelled\"}";
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("webhook-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        service.handleWebhook(body, signature);
        verifyNoInteractions(jdbc);
    }

    @Test
    void ignoresDuplicateProviderEventIdBeforeApplyingPaymentSideEffects() throws Exception {
        String body = "{\\"event\\":\\"payment_link.paid\\"}";
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
        String body = "{\\"event\\":\\"payment_link.paid\\",\\"payload\\":{\\"payment_link\\":{\\"entity\\":{\\"id\\":\\"plink_late\\",\\"amount_paid\\":2500,\\"currency\\":\\"INR\\"}},\\"payment\\":{\\"entity\\":{\\"id\\":\\"pay_late\\"}}}}";

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
        String body = "{\\"event\\":\\"payment_link.paid\\",\\"payload\\":{\\"payment_link\\":{\\"entity\\":{\\"id\\":\\"plink_currency\\",\\"amount_paid\\":2500,\\"currency\\":\\"USD\\"}}}}";

        service.handleWebhook(body, signature(body));

        org.mockito.Mockito.verify(jdbc).update(
            org.mockito.ArgumentMatchers.contains("status='REVIEW_REQUIRED'"),
            (Object[]) org.mockito.ArgumentMatchers.any(Object[].class));
        org.mockito.Mockito.verify(jdbc, org.mockito.Mockito.never()).update(
            org.mockito.ArgumentMatchers.contains("UPDATE bookings SET status='CONFIRMED'"),
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

}
