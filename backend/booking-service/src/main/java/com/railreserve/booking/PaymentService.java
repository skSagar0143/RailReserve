package com.railreserve.booking;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

@Service
public class PaymentService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final RestClient restClient;
    private final String keyId;
    private final String keySecret;
    private final String webhookSecret;
    private final String callbackUrl;
    private final String currency;
    private final boolean enabled;

    public PaymentService(JdbcTemplate jdbc, ObjectMapper mapper,
            @Value("${railreserve.payment.key-id:}") String keyId,
            @Value("${railreserve.payment.key-secret:}") String keySecret,
            @Value("${railreserve.payment.webhook-secret:}") String webhookSecret,
            @Value("${railreserve.payment.callback-url:http://localhost:5173/payment-result}") String callbackUrl,
            @Value("${railreserve.payment.currency:INR}") String currency,
            @Value("${railreserve.payment.enabled:false}") boolean enabled) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.keyId = keyId;
        this.keySecret = keySecret;
        this.webhookSecret = webhookSecret;
        this.callbackUrl = callbackUrl;
        this.currency = currency;
        this.enabled = enabled;
        this.restClient = RestClient.builder().baseUrl("https://api.razorpay.com/v1").build();
    }

    @Transactional
    public PaymentDtos.CheckoutResponse createCheckout(UUID customerId, UUID bookingId) {
        if (customerId == null || bookingId == null) throw new IllegalArgumentException("Customer and booking identities are required.");
        if (!enabled || keyId.isBlank() || keySecret.isBlank() || webhookSecret.isBlank()) {
            throw new IllegalStateException("Online payment is not configured. Set the Razorpay test/live credentials and webhook secret.");
        }
        List<Map<String,Object>> bookingRows = jdbc.queryForList(
            "SELECT booking_reference,status,total_amount,hold_expires_at,customer_id FROM bookings WHERE id=? FOR UPDATE", uuidBytes(bookingId));
        if (bookingRows.isEmpty()) throw new BookingConflictException("Booking was not found.");
        Map<String,Object> booking = bookingRows.getFirst();
        if (!customerId.equals(uuidFromBytes((byte[]) booking.get("customer_id")))) throw new AccessDeniedException("Booking belongs to another customer.");
        String bookingStatus = String.valueOf(booking.get("status"));
        if (!"PENDING_PAYMENT".equals(bookingStatus)) throw new BookingConflictException("Only a booking awaiting payment can be paid.");
        java.sql.Timestamp expires = (java.sql.Timestamp) booking.get("hold_expires_at");
        if (expires == null || !expires.toInstant().isAfter(Instant.now())) throw new BookingConflictException("The seat hold has expired. Create a new booking.");
        List<Map<String,Object>> existing = jdbc.queryForList(
            "SELECT provider_link_id,payment_url,amount,currency,status FROM payments WHERE booking_id=?", uuidBytes(bookingId));
        if (!existing.isEmpty()) {
            Map<String,Object> p = existing.getFirst();
            if (!"CREATED".equals(String.valueOf(p.get("status")))) throw new BookingConflictException("This payment attempt is no longer payable.");
            return new PaymentDtos.CheckoutResponse(bookingId.toString(), String.valueOf(booking.get("booking_reference")),
                String.valueOf(p.get("provider_link_id")), String.valueOf(p.get("payment_url")),
                (java.math.BigDecimal) p.get("amount"), String.valueOf(p.get("currency")), "CREATED");
        }
        java.math.BigDecimal amount = (java.math.BigDecimal) booking.get("total_amount");
        long paise;
        try { paise = amount.movePointRight(2).longValueExact(); }
        catch (ArithmeticException e) { throw new BookingConflictException("Booking amount cannot be represented in the payment currency."); }
        Map<String,Object> request = Map.of(
            "amount", paise, "currency", currency, "accept_partial", false,
            "reference_id", bookingId.toString(),
            "description", "RailReserve booking " + booking.get("booking_reference"),
            "callback_url", callbackUrl, "callback_method", "get",
            "reminder_enable", false,
            "notes", Map.of("bookingId", bookingId.toString(), "bookingReference", String.valueOf(booking.get("booking_reference"))));
        Map response;
        try {
            response = restClient.post().uri("/payment_links").contentType(MediaType.APPLICATION_JSON)
                .headers(h -> h.setBasicAuth(keyId, keySecret)).body(request).retrieve().body(Map.class);
        } catch (Exception e) {
            throw new IllegalStateException("Payment provider could not create checkout. The seat hold remains active until its expiry.", e);
        }
        if (response == null || response.get("id") == null || response.get("short_url") == null) {
            throw new IllegalStateException("Payment provider returned an incomplete checkout response.");
        }
        String linkId = String.valueOf(response.get("id"));
        String url = String.valueOf(response.get("short_url"));
        jdbc.update("INSERT INTO payments(id,booking_id,provider,provider_link_id,amount,currency,status,payment_url) VALUES(?,?,?,?,?,?,?,?)",
            uuidBytes(UUID.randomUUID()), uuidBytes(bookingId), "RAZORPAY", linkId, amount, currency, "CREATED", url);
        return new PaymentDtos.CheckoutResponse(bookingId.toString(), String.valueOf(booking.get("booking_reference")),
            linkId, url, amount, currency, "CREATED");
    }

    @Transactional(readOnly = true)
    public PaymentDtos.PaymentStatus status(UUID customerId, UUID bookingId) {
        if (customerId == null || bookingId == null) throw new IllegalArgumentException("Customer and booking identities are required.");
        List<Map<String,Object>> rows = jdbc.queryForList(
            "SELECT b.booking_reference,b.status AS booking_status,b.customer_id,b.total_amount,p.status AS payment_status,p.provider_link_id,p.payment_url,p.currency,p.paid_at FROM bookings b LEFT JOIN payments p ON p.booking_id=b.id WHERE b.id=?",
            uuidBytes(bookingId));
        if (rows.isEmpty()) throw new BookingConflictException("Booking was not found.");
        Map<String,Object> row=rows.getFirst();
        if (!customerId.equals(uuidFromBytes((byte[]) row.get("customer_id")))) throw new AccessDeniedException("Booking belongs to another customer.");
        return new PaymentDtos.PaymentStatus(bookingId.toString(),String.valueOf(row.get("booking_reference")),
            String.valueOf(row.get("booking_status")),row.get("payment_status")==null?"NOT_STARTED":String.valueOf(row.get("payment_status")),
            (String)row.get("provider_link_id"),(String)row.get("payment_url"),(java.math.BigDecimal)row.get("total_amount"),
            row.get("currency")==null?currency:String.valueOf(row.get("currency")),
            row.get("paid_at") instanceof java.sql.Timestamp t?t.toInstant():null);
    }

    @Transactional(readOnly = true)
    public List<PaymentDtos.ReconciliationItem> reconciliationQueue(int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 100));
        return jdbc.query(
            "SELECT p.id,p.booking_id,b.booking_reference,b.customer_id,p.provider,p.provider_link_id,p.provider_payment_id,p.status,p.amount,p.currency,p.created_at,p.updated_at " +
            "FROM payments p JOIN bookings b ON b.id=p.booking_id " +
            "WHERE p.status IN ('REVIEW_REQUIRED','PAID_AFTER_EXPIRY','REFUND_REQUESTED','REFUND_REVIEW_REQUIRED','REFUND_PENDING') ORDER BY p.updated_at ASC LIMIT ?",
            (rs, rowNum) -> new PaymentDtos.ReconciliationItem(
                uuidFromBytes(rs.getBytes("id")).toString(),
                uuidFromBytes(rs.getBytes("booking_id")).toString(),
                rs.getString("booking_reference"),
                uuidFromBytes(rs.getBytes("customer_id")).toString(),
                rs.getString("provider"),
                rs.getString("provider_link_id"),
                rs.getString("provider_payment_id"),
                rs.getString("status"),
                rs.getBigDecimal("amount"),
                rs.getString("currency"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()),
            limit);
    }


    /**
     * Durable refund intent. A request is persisted before contacting Razorpay; if the
     * network outcome is ambiguous, the same payment is never automatically submitted twice.
     */
    public record PreparedRefund(UUID refundId, UUID paymentId, String providerPaymentId,
            String reason, String status, String providerRefundId, Instant createdAt, boolean newlyCreated) {}

    @Transactional
    public PreparedRefund prepareRefund(UUID paymentId, String reason, String requestedBy) {
        if (paymentId == null) throw new IllegalArgumentException("Payment identity is required.");
        if (reason == null || reason.isBlank() || reason.length() > 500) {
            throw new IllegalArgumentException("Refund reason must contain 1 to 500 characters.");
        }
        String actor = requestedBy == null || requestedBy.isBlank() ? "unknown" : requestedBy;
        if (actor.length() > 128) actor = actor.substring(0, 128);
        List<Map<String,Object>> rows = jdbc.queryForList(
            "SELECT id,provider,provider_payment_id,status FROM payments WHERE id=? FOR UPDATE", uuidBytes(paymentId));
        if (rows.isEmpty()) throw new BookingConflictException("Payment was not found.");
        Map<String,Object> payment = rows.getFirst();
        if (!"RAZORPAY".equals(String.valueOf(payment.get("provider")))) {
            throw new BookingConflictException("Refunds are not supported for this payment provider.");
        }
        String providerPaymentId = (String) payment.get("provider_payment_id");
        if (providerPaymentId == null || providerPaymentId.isBlank()) {
            throw new BookingConflictException("The provider payment ID is missing; verify the payment with the provider before refunding.");
        }
        List<Map<String,Object>> existing = jdbc.queryForList(
            "SELECT id,status,provider_refund_id,reason,created_at FROM payment_refunds WHERE payment_id=?",
            uuidBytes(paymentId));
        if (!existing.isEmpty()) {
            Map<String,Object> refund = existing.getFirst();
            return new PreparedRefund(uuidFromBytes((byte[]) refund.get("id")), paymentId, providerPaymentId,
                String.valueOf(refund.get("reason")), String.valueOf(refund.get("status")),
                (String) refund.get("provider_refund_id"), ((java.sql.Timestamp) refund.get("created_at")).toInstant(), false);
        }
        String paymentStatus = String.valueOf(payment.get("status"));
        if (!List.of("PAID_AFTER_EXPIRY", "REVIEW_REQUIRED").contains(paymentStatus)) {
            throw new BookingConflictException("Only paid-after-expiry or amount/currency-review payments can be refunded through this reconciliation action. Confirmed bookings require the cancellation workflow first.");
        }
        UUID refundId = UUID.randomUUID();
        jdbc.update("INSERT INTO payment_refunds(id,payment_id,status,reason,requested_by) VALUES(?,?,?,?,?)",
            uuidBytes(refundId), uuidBytes(paymentId), "REQUESTED", reason.trim(), actor);
        jdbc.update("UPDATE payments SET status='REFUND_REQUESTED' WHERE id=?", uuidBytes(paymentId));
        auditRefund(refundId, paymentId, "PAYMENT_REFUND_REQUESTED", "REQUESTED", reason.trim());
        return new PreparedRefund(refundId, paymentId, providerPaymentId, reason.trim(), "REQUESTED", null, Instant.now(), true);
    }

    /** Submit a full refund. Amount is intentionally omitted so the provider refunds the captured amount,
     * including when the originally received amount differed from the booking amount. */
    public Map<String,Object> submitRefund(PreparedRefund refund) {
        Map<String,Object> request = Map.of("notes", Map.of(
            "railreserveRefundId", refund.refundId().toString(),
            "reason", refund.reason().substring(0, Math.min(refund.reason().length(), 250))));
        Map response = restClient.post().uri("/payments/{paymentId}/refund", refund.providerPaymentId())
            .contentType(MediaType.APPLICATION_JSON)
            .headers(h -> h.setBasicAuth(keyId, keySecret))
            .body(request).retrieve().body(Map.class);
        if (response == null || response.get("id") == null || response.get("status") == null) {
            throw new IllegalStateException("Payment provider returned an incomplete refund response; provider status must be reconciled manually.");
        }
        return response;
    }

    @Transactional
    public PaymentDtos.RefundResponse recordRefundResult(PreparedRefund refund, Map<String,Object> providerResponse) {
        String providerRefundId = String.valueOf(providerResponse.get("id"));
        String providerStatus = String.valueOf(providerResponse.get("status")).toLowerCase(java.util.Locale.ROOT);
        String status = "processed".equals(providerStatus) ? "REFUNDED"
            : "pending".equals(providerStatus) ? "REFUND_PENDING" : "REFUND_REVIEW_REQUIRED";
        jdbc.update("UPDATE payment_refunds SET provider_refund_id=?,status=? WHERE id=?",
            providerRefundId, status, uuidBytes(refund.refundId()));
        jdbc.update("UPDATE payments SET status=? WHERE id=?", status, uuidBytes(refund.paymentId()));
        auditRefund(refund.refundId(), refund.paymentId(), "PAYMENT_REFUND_RESULT", status, providerRefundId);
        return new PaymentDtos.RefundResponse(refund.refundId().toString(), refund.paymentId().toString(), status,
            providerRefundId, refundMessage(status), refund.createdAt());
    }

    @Transactional
    public PaymentDtos.RefundResponse markRefundUncertain(PreparedRefund refund) {
        jdbc.update("UPDATE payment_refunds SET status='REFUND_REVIEW_REQUIRED' WHERE id=? AND status='REQUESTED'",
            uuidBytes(refund.refundId()));
        jdbc.update("UPDATE payments SET status='REFUND_REVIEW_REQUIRED' WHERE id=? AND status='REFUND_REQUESTED'",
            uuidBytes(refund.paymentId()));
        auditRefund(refund.refundId(), refund.paymentId(), "PAYMENT_REFUND_UNCERTAIN", "REFUND_REVIEW_REQUIRED",
            "Provider outcome uncertain; manual reconciliation required.");
        return new PaymentDtos.RefundResponse(refund.refundId().toString(), refund.paymentId().toString(),
            "REFUND_REVIEW_REQUIRED", null,
            "The provider outcome is uncertain. Check the Razorpay dashboard before taking any further action; this request will not be resubmitted automatically.",
            refund.createdAt());
    }

    public PaymentDtos.RefundResponse refundResponse(PreparedRefund refund) {
        return new PaymentDtos.RefundResponse(refund.refundId().toString(), refund.paymentId().toString(),
            refund.status(), refund.providerRefundId(), refundMessage(refund.status()), refund.createdAt());
    }


    private void auditRefund(UUID refundId, UUID paymentId, String action, String outcome, String details) {
        List<Map<String,Object>> rows = jdbc.queryForList(
            "SELECT requested_by FROM payment_refunds WHERE id=?", uuidBytes(refundId));
        String actor = rows.isEmpty() ? "unknown" : String.valueOf(rows.getFirst().get("requested_by"));
        jdbc.update("INSERT INTO admin_audit_events(actor,action,target_type,target_id,outcome,details) VALUES(?,?,?,?,?,?)",
            actor, action, "PAYMENT", paymentId.toString(), outcome, details);
    }

    private static String refundMessage(String status) {
        return switch (status) {
            case "REFUNDED" -> "Razorpay reports that the refund was processed.";
            case "REFUND_PENDING" -> "Razorpay accepted the refund; final settlement is pending.";
            case "REFUND_REVIEW_REQUIRED" -> "Check the Razorpay dashboard and reconcile the refund manually.";
            case "REQUESTED" -> "A refund request is already recorded and was not submitted again to avoid a duplicate refund.";
            default -> "Refund request status: " + status;
        };
    }

    @Transactional
    public void handleWebhook(String rawBody, String signature) {
        handleWebhook(rawBody, signature, null);
    }

    @Transactional
    public void handleWebhook(String rawBody, String signature, String providerEventId) {
        if (webhookSecret.isBlank()) throw new IllegalStateException("Payment webhook secret is not configured.");
        if (rawBody == null || signature == null || !constantTimeEquals(hmac(rawBody, webhookSecret), signature)) {
            throw new org.springframework.security.access.AccessDeniedException("Invalid payment webhook signature.");
        }
        Map<String,Object> event;
        try { event = mapper.readValue(rawBody, Map.class); }
        catch (Exception e) { throw new IllegalArgumentException("Invalid payment webhook payload."); }
        String eventType = String.valueOf(event.get("event"));
        // Razorpay may redeliver the same event. Prefer its event ID; if the header is
        // absent, use a deterministic body fingerprint so exact retries are still deduplicated.
        // This fallback is deliberately not presented as provider-level event identity.
        String deduplicationId = providerEventId == null || providerEventId.isBlank()
            ? "body-sha256-" + sha256(rawBody)
            : providerEventId.trim();
        if (deduplicationId.length() > 128) {
            throw new IllegalArgumentException("Payment provider event ID exceeds the supported length.");
        }
        int inserted = jdbc.update("INSERT IGNORE INTO payment_webhook_events(event_id,event_type) VALUES(?,?)",
            deduplicationId, eventType);
        if (inserted == 0) return;
        if (!"payment_link.paid".equals(eventType)) return;
        Map<String,Object> payload=(Map<String,Object>)event.get("payload");
        if(payload==null)return;
        Map<String,Object> linkPart=(Map<String,Object>)payload.get("payment_link");
        if(linkPart==null)return;
        Map<String,Object> link=(Map<String,Object>)linkPart.get("entity");
        if(link==null || link.get("id")==null)return;
        String linkId=String.valueOf(link.get("id"));
        Long paidAmount = link.get("amount_paid") instanceof Number n ? n.longValue() : null;
        String paidCurrency = link.get("currency") == null ? null : String.valueOf(link.get("currency"));
        String providerPaymentId=null;
        Object paymentPart=payload.get("payment");
        if(paymentPart instanceof Map<?,?> pm && pm.get("entity") instanceof Map<?,?> pe && pe.get("id")!=null) providerPaymentId=String.valueOf(pe.get("id"));
        confirmPaidLink(linkId,providerPaymentId,paidAmount,paidCurrency);
    }

    private void confirmPaidLink(String linkId,String paymentId,Long paidAmount,String paidCurrency) {
        List<Map<String,Object>> rows=jdbc.queryForList(
            "SELECT p.id,p.booking_id,p.status,p.amount,p.currency,b.status AS booking_status,b.booking_reference,b.customer_id,b.hold_expires_at FROM payments p JOIN bookings b ON b.id=p.booking_id WHERE p.provider='RAZORPAY' AND p.provider_link_id=? FOR UPDATE",linkId);
        if(rows.isEmpty()) return;
        Map<String,Object> row=rows.getFirst();
        if("PAID".equals(String.valueOf(row.get("status")))) return;
        if(!"CREATED".equals(String.valueOf(row.get("status")))) return;
        UUID bookingId=uuidFromBytes((byte[])row.get("booking_id"));
        java.math.BigDecimal expectedAmount=(java.math.BigDecimal)row.get("amount");
        long expectedPaise=expectedAmount.movePointRight(2).longValue();
        if(paidAmount==null || paidAmount.longValue()!=expectedPaise || paidCurrency==null || !paidCurrency.equals(String.valueOf(row.get("currency")))) {
            jdbc.update("UPDATE payments SET status='REVIEW_REQUIRED',provider_payment_id=? WHERE id=?",paymentId,row.get("id"));
            return;
        }
        java.sql.Timestamp expires=(java.sql.Timestamp)row.get("hold_expires_at");
        if(!"PENDING_PAYMENT".equals(String.valueOf(row.get("booking_status"))) || expires==null || !expires.toInstant().isAfter(Instant.now())) {
            jdbc.update("UPDATE payments SET status='PAID_AFTER_EXPIRY',provider_payment_id=? WHERE id=?",paymentId, row.get("id"));
            return;
        }
        jdbc.update("UPDATE payments SET status='PAID',provider_payment_id=?,paid_at=CURRENT_TIMESTAMP(6) WHERE id=?",paymentId,row.get("id"));
        jdbc.update("UPDATE bookings SET status='CONFIRMED' WHERE id=? AND status='PENDING_PAYMENT'",row.get("booking_id"));
        jdbc.update("UPDATE seat_inventory si JOIN booking_seats bs ON bs.inventory_id=si.id SET si.status='BOOKED',si.held_until=NULL,si.version=si.version+1 WHERE bs.booking_id=? AND si.status='HELD'",row.get("booking_id"));
        Map<String,Object> payload=Map.of("eventId",UUID.randomUUID().toString(),"bookingId",bookingId.toString(),
            "bookingReference",String.valueOf(row.get("booking_reference")),"customerId",uuidFromBytes((byte[])row.get("customer_id")).toString(),
            "paymentId",paymentId==null?"":paymentId);
        try {
            jdbc.update("INSERT INTO outbox_events(event_id,aggregate_type,aggregate_id,event_type,payload) VALUES(?,?,?,?,?)",
                uuidBytes(UUID.fromString(String.valueOf(payload.get("eventId")))),"Booking",bookingId.toString(),"BookingConfirmed",mapper.writeValueAsString(payload));
        } catch(Exception e) { throw new IllegalStateException("Could not persist booking confirmation event.",e); }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to fingerprint payment webhook payload.", e);
        }
    }

    private static String hmac(String body,String secret) {
        try { Mac mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256")); return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8))); }
        catch(Exception e){throw new IllegalStateException("Unable to verify payment webhook.",e);}
    }
    private static boolean constantTimeEquals(String expected,String actual) {
        try { return MessageDigest.isEqual(HexFormat.of().parseHex(expected),HexFormat.of().parseHex(actual)); }
        catch(IllegalArgumentException e){return false;}
    }
    private static byte[] uuidBytes(UUID uuid) { return ByteBuffer.allocate(16).putLong(uuid.getMostSignificantBits()).putLong(uuid.getLeastSignificantBits()).array(); }
    private static UUID uuidFromBytes(byte[] bytes) { ByteBuffer b=ByteBuffer.wrap(bytes); return new UUID(b.getLong(),b.getLong()); }
}