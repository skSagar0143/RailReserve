package com.railreserve.train;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.sql.Date;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class TrainAdminServiceTest {
    @Mock JdbcTemplate jdbc;
    @Mock StringRedisTemplate redis;
    @InjectMocks TrainAdminService service;

    @Test
    void cancelsScheduledServiceDateAndWritesAudit() {
        when(jdbc.update(eq("UPDATE train_service_calendar SET status='CANCELLED' WHERE id=? AND train_id=? AND service_date>=? AND status='SCHEDULED'"),
            eq(22L), eq(10L), any(Date.class))).thenReturn(1);

        var result = service.cancelServiceDate(10L, 22L, "admin@example.test");

        assertEquals(22L, result.id());
        assertEquals("CANCELLED", result.status());
        verify(jdbc).update(
            eq("INSERT INTO train_admin_audit_events(actor,action,target_type,target_id,outcome,details) VALUES(?,?,?,?,?,?)"),
            eq("admin@example.test"), eq("SERVICE_DATE_CANCELLED"), eq("TRAIN_SERVICE_DATE"), eq("22"),
            eq("CANCELLED"), eq("Cancelled scheduled service date for train 10"));
    }

    @Test
    void rejectsCancellationWhenServiceDateIsNotScheduledOrAlreadyPast() {
        when(jdbc.update(eq("UPDATE train_service_calendar SET status='CANCELLED' WHERE id=? AND train_id=? AND service_date>=? AND status='SCHEDULED'"),
            eq(22L), eq(10L), any(Date.class))).thenReturn(0);

        assertThrows(TrainAdminConflictException.class, () -> service.cancelServiceDate(10L, 22L, "admin"));
        verifyNoMoreInteractions(jdbc);
    }

    @Test
    void rejectsInvalidIdsBeforeDatabaseAccess() {
        assertThrows(IllegalArgumentException.class, () -> service.cancelServiceDate(0L, 22L, "admin"));
        assertThrows(IllegalArgumentException.class, () -> service.cancelServiceDate(10L, 0L, "admin"));
        verifyNoMoreInteractions(jdbc);
    }
    @Test
    void deactivatesTrainAndWritesAudit() {
        when(jdbc.queryForList("SELECT active FROM trains WHERE id=? FOR UPDATE", 10L))
            .thenReturn(java.util.List.of(java.util.Map.of("active", true)));
        when(jdbc.update("UPDATE trains SET active=? WHERE id=?", false, 10L)).thenReturn(1);

        var result = service.updateTrainStatus(10L, false, "admin@example.test");

        assertEquals("INACTIVE", result.status());
        verify(jdbc).update(
            eq("INSERT INTO train_admin_audit_events(actor,action,target_type,target_id,outcome,details) VALUES(?,?,?,?,?,?)"),
            eq("admin@example.test"), eq("TRAIN_STATUS_UPDATED"), eq("TRAIN"), eq("10"), eq("INACTIVE"),
            eq("Active flag changed from true to false"));
    }

    @Test
    void doesNotWriteAuditOrInvalidateCacheWhenStatusIsAlreadyRequestedValue() {
        when(jdbc.queryForList("SELECT active FROM stations WHERE id=? FOR UPDATE", 4L))
            .thenReturn(java.util.List.of(java.util.Map.of("active", true)));

        var result = service.updateStationStatus(4L, true, "admin");

        assertEquals("ACTIVE", result.status());
        verifyNoMoreInteractions(jdbc);
    }

    @Test
    void rejectsStatusChangeForMissingResource() {
        when(jdbc.queryForList("SELECT active FROM trains WHERE id=? FOR UPDATE", 10L))
            .thenReturn(java.util.List.of());

        assertThrows(TrainAdminConflictException.class, () -> service.updateTrainStatus(10L, false, "admin"));
        verifyNoMoreInteractions(jdbc);
    }

}
