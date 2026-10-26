package com.railreserve.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class InventoryAdminServiceTest {
    @Mock JdbcTemplate jdbc;
    @InjectMocks InventoryAdminService service;

    @Test
    void updatesFareOnlyForAvailableSeatAndAuditsChange() {
        when(jdbc.queryForList("SELECT status,fare_amount FROM seat_inventory WHERE id=? FOR UPDATE", 12L))
            .thenReturn(List.of(Map.of("status", "AVAILABLE", "fare_amount", new BigDecimal("450.00"))));
        when(jdbc.update(eq("UPDATE seat_inventory SET fare_amount=?,version=version+1 WHERE id=? AND status='AVAILABLE'"),
            eq(new BigDecimal("500.00")), eq(12L))).thenReturn(1);

        var result = service.updateFare(12L, new BigDecimal("500.00"), "admin@example.test");

        assertEquals(12L, result.inventoryId());
        assertEquals(new BigDecimal("450.00"), result.previousFare());
        assertEquals(new BigDecimal("500.00"), result.currentFare());
        verify(jdbc).update(
            eq("INSERT INTO admin_audit_events(actor,action,target_type,target_id,outcome,details) VALUES(?,?,?,?,?,?)"),
            eq("admin@example.test"), eq("INVENTORY_FARE_UPDATED"), eq("SEAT_INVENTORY"), eq("12"), eq("UPDATED"),
            eq("Fare changed from 450.00 to 500.00"));
    }

    @Test
    void refusesFareChangeForHeldOrBookedSeat() {
        when(jdbc.queryForList("SELECT status,fare_amount FROM seat_inventory WHERE id=? FOR UPDATE", 12L))
            .thenReturn(List.of(Map.of("status", "HELD", "fare_amount", new BigDecimal("450.00"))));

        assertThrows(BookingConflictException.class,
            () -> service.updateFare(12L, new BigDecimal("500.00"), "admin"));
        verifyNoMoreInteractions(jdbc);
    }

    @Test
    void rejectsInvalidFareBeforeDatabaseAccess() {
        assertThrows(IllegalArgumentException.class,
            () -> service.updateFare(12L, new BigDecimal("0.00"), "admin"));
        assertThrows(IllegalArgumentException.class,
            () -> service.updateFare(12L, new BigDecimal("10.999"), "admin"));
        verifyNoMoreInteractions(jdbc);
    }

    @Test
    void rejectsMissingInventory() {
        when(jdbc.queryForList("SELECT status,fare_amount FROM seat_inventory WHERE id=? FOR UPDATE", 99L))
            .thenReturn(List.of());
        assertThrows(BookingConflictException.class,
            () -> service.updateFare(99L, new BigDecimal("500.00"), "admin"));
        verifyNoMoreInteractions(jdbc);
    }
    @Test
    void rejectsNonPositiveInventoryIdBeforeDatabaseAccess() {
        assertThrows(IllegalArgumentException.class,
            () -> service.updateFare(0L, new BigDecimal("500.00"), "admin"));
        assertThrows(IllegalArgumentException.class,
            () -> service.updateFare(-1L, new BigDecimal("500.00"), "admin"));
        verifyNoMoreInteractions(jdbc);
    }

    @Test
    void rejectsConcurrentSeatStateChangeAndDoesNotWriteAudit() {
        when(jdbc.queryForList("SELECT status,fare_amount FROM seat_inventory WHERE id=? FOR UPDATE", 12L))
            .thenReturn(List.of(Map.of("status", "AVAILABLE", "fare_amount", new BigDecimal("450.00"))));
        when(jdbc.update(eq("UPDATE seat_inventory SET fare_amount=?,version=version+1 WHERE id=? AND status='AVAILABLE'"),
            eq(new BigDecimal("500.00")), eq(12L))).thenReturn(0);

        assertThrows(BookingConflictException.class,
            () -> service.updateFare(12L, new BigDecimal("500.00"), "admin"));

        verify(jdbc).update(
            eq("UPDATE seat_inventory SET fare_amount=?,version=version+1 WHERE id=? AND status='AVAILABLE'"),
            eq(new BigDecimal("500.00")), eq(12L));
        verifyNoMoreInteractions(jdbc);
    }

    @Test
    void normalizesOversizedAuditActorSafely() {
        when(jdbc.queryForList("SELECT status,fare_amount FROM seat_inventory WHERE id=? FOR UPDATE", 12L))
            .thenReturn(List.of(Map.of("status", "AVAILABLE", "fare_amount", new BigDecimal("450.00"))));
        when(jdbc.update(eq("UPDATE seat_inventory SET fare_amount=?,version=version+1 WHERE id=? AND status='AVAILABLE'"),
            eq(new BigDecimal("500.00")), eq(12L))).thenReturn(1);

        service.updateFare(12L, new BigDecimal("500.00"), "x".repeat(140));

        verify(jdbc).update(
            eq("INSERT INTO admin_audit_events(actor,action,target_type,target_id,outcome,details) VALUES(?,?,?,?,?,?)"),
            eq("x".repeat(128)), eq("INVENTORY_FARE_UPDATED"), eq("SEAT_INVENTORY"), eq("12"), eq("UPDATED"),
            eq("Fare changed from 450.00 to 500.00"));
    }

}
