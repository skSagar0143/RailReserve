package com.railreserve.booking;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class PaymentWebhookDeduplicationTest {
    @Mock JdbcTemplate jdbc;

    @Test
    void duplicateProviderEventIsAcknowledgedWithoutReapplyingSideEffects() throws Exception {
        PaymentService service = new PaymentService(jdbc, new ObjectMapper(), "rzp_test_key", "key-secret",
            "webhook-secret", "http://localhost:5173/payment-result", "INR", true);
        String body = "{\"event\":\"payment_link.cancelled\"}";
        String signature = signature(body);
        doReturn(0).when(jdbc).update(contains("INSERT IGNORE INTO payment_webhook_events"), (Object[]) any(Object[].class));

        assertDoesNotThrow(() -> service.handleWebhook(body, signature, "evt_duplicate_1"));

        verify(jdbc).update(contains("INSERT IGNORE INTO payment_webhook_events"), (Object[]) any(Object[].class));
        verifyNoMoreInteractions(jdbc);
    }

    private static String signature(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("webhook-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }
}
