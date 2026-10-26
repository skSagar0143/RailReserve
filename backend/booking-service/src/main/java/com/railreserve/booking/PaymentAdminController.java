package com.railreserve.booking;

import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/payments")
public class PaymentAdminController {
    private final PaymentService payments;

    public PaymentAdminController(PaymentService payments) {
        this.payments = payments;
    }

    /**
     * Lists payments requiring operator reconciliation, including uncertain refund outcomes.
     */
    @GetMapping("/reconciliation")
    public List<PaymentDtos.ReconciliationItem> reconciliation(
            @RequestParam(defaultValue = "50") int limit) {
        return payments.reconciliationQueue(limit);
    }

    /**
     * Starts a single full refund for a captured payment. The durable request is written
     * before the provider call; retries return the existing request rather than submitting
     * a second refund. If the provider outcome is uncertain, an operator must reconcile it.
     */
    @PostMapping("/{paymentId}/refund")
    public PaymentDtos.RefundResponse refund(@PathVariable UUID paymentId,
            @RequestBody PaymentDtos.RefundRequest request, Authentication authentication) {
        if (request == null) throw new IllegalArgumentException("Refund request body is required.");
        PaymentService.PreparedRefund prepared = payments.prepareRefund(
            paymentId, request.reason(), authentication == null ? "unknown" : authentication.getName());
        if (!prepared.newlyCreated()) return payments.refundResponse(prepared);
        java.util.Map<String,Object> providerResponse;
        try {
            providerResponse = payments.submitRefund(prepared);
        } catch (Exception providerFailure) {
            return payments.markRefundUncertain(prepared);
        }
        return payments.recordRefundResult(prepared, providerResponse);
    }
}
