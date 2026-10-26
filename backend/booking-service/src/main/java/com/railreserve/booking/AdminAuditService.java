package com.railreserve.booking;

import java.sql.Timestamp;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAuditService {
    private final JdbcTemplate jdbc;

    public AdminAuditService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true)
    public List<AdminAuditDtos.AuditEvent> list(String requestedActor, String requestedAction, int requestedLimit) {
        String actor = normalizeFilter(requestedActor, 128, "actor");
        String action = normalizeFilter(requestedAction, 100, "action");
        int limit = Math.max(1, Math.min(requestedLimit, 100));
        return jdbc.query("""
            SELECT id,actor,action,target_type,target_id,outcome,details,created_at
            FROM admin_audit_events
            WHERE (? IS NULL OR actor=?)
              AND (? IS NULL OR action=?)
            ORDER BY id DESC
            LIMIT ?
            """,
            (rs, rowNum) -> {
                Timestamp createdAt = rs.getTimestamp("created_at");
                return new AdminAuditDtos.AuditEvent(rs.getLong("id"), rs.getString("actor"),
                    rs.getString("action"), rs.getString("target_type"), rs.getString("target_id"),
                    rs.getString("outcome"), rs.getString("details"),
                    createdAt == null ? null : createdAt.toInstant());
            }, actor, actor, action, action, limit);
    }

    private static String normalizeFilter(String value, int maxLength, String name) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.strip();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException("Audit " + name + " filter exceeds " + maxLength + " characters.");
        }
        return normalized;
    }
}
