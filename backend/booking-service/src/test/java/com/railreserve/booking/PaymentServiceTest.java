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
    void refusesCheckoutWhenPaymentIsDisabled() {
        PaymentService disabled = new PaymentService(jdbc, new ObjectMapper(), "", "", "",
            "http://localhost:5173/payment-result", "INR", false);
        assertThrows(IllegalStateException.class, () -> disabled.createCheckout(java.util.UUID.randomUUID(), java.util.UUID.randomUUID()));
        verifyNoInteractions(jdbc);
    }
}