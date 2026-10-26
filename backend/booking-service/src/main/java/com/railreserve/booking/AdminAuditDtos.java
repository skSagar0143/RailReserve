package com.railreserve.booking;

import java.time.Instant;

public final class AdminAuditDtos {
    private AdminAuditDtos() {}
    public record AuditEvent(long id, String actor, String action, String targetType, String targetId,
            String outcome, String details, Instant createdAt) {}
}
