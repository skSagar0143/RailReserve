package com.railreserve.booking;

import java.util.List;
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
     * Lists paid-after-expiry and amount/currency mismatch payments for operator review.
     * This endpoint intentionally does not trigger refunds or change payment state.
     */
    @GetMapping("/reconciliation")
    public List<PaymentDtos.ReconciliationItem> reconciliation(
            @RequestParam(defaultValue = "50") int limit) {
        return payments.reconciliationQueue(limit);
    }
}
