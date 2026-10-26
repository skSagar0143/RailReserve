package com.railreserve.booking;

import java.sql.PreparedStatement;
import java.sql.Statement;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryAdminService {
    private final JdbcTemplate jdbc;
    public InventoryAdminService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public InventoryAdminDtos.CreatedSeat create(InventoryAdminDtos.CreateSeatRequest request) {
        if (request.journeyDate().isBefore(java.time.LocalDate.now())) {
            throw new IllegalArgumentException("Journey date cannot be in the past.");
        }
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO seat_inventory(train_id,journey_date,coach_code,seat_number,status,fare_amount) VALUES(?,?,?,?, 'AVAILABLE', ?)",
                Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, request.trainId());
            ps.setDate(2, java.sql.Date.valueOf(request.journeyDate()));
            ps.setString(3, request.coachCode().trim().toUpperCase(java.util.Locale.ROOT));
            ps.setString(4, request.seatNumber().trim().toUpperCase(java.util.Locale.ROOT));
            ps.setBigDecimal(5, request.fareAmount());
            return ps;
        }, key);
        return new InventoryAdminDtos.CreatedSeat(key.getKey().longValue(), "AVAILABLE");
    }
    @Transactional
    public InventoryAdminDtos.FareUpdated updateFare(long inventoryId, java.math.BigDecimal newFare, String actor) {
        if (inventoryId <= 0) throw new IllegalArgumentException("Inventory ID must be positive.");
        if (newFare == null || newFare.signum() <= 0 || newFare.scale() > 2) {
            throw new IllegalArgumentException("Fare must be positive and contain no more than two decimal places.");
        }
        var rows = jdbc.queryForList(
            "SELECT status,fare_amount FROM seat_inventory WHERE id=? FOR UPDATE", inventoryId);
        if (rows.isEmpty()) throw new BookingConflictException("Seat inventory was not found.");
        var row = rows.getFirst();
        String status = String.valueOf(row.get("status"));
        if (!"AVAILABLE".equals(status)) {
            throw new BookingConflictException("Fare can only be changed while a seat is AVAILABLE.");
        }
        java.math.BigDecimal previousFare = (java.math.BigDecimal) row.get("fare_amount");
        int updated = jdbc.update(
            "UPDATE seat_inventory SET fare_amount=?,version=version+1 WHERE id=? AND status='AVAILABLE'",
            newFare, inventoryId);
        if (updated != 1) throw new BookingConflictException("Seat inventory changed while updating the fare; retry the request.");
        String safeActor = actor == null || actor.isBlank() ? "unknown" : actor.substring(0, Math.min(actor.length(), 128));
        jdbc.update(
            "INSERT INTO admin_audit_events(actor,action,target_type,target_id,outcome,details) VALUES(?,?,?,?,?,?)",
            safeActor, "INVENTORY_FARE_UPDATED", "SEAT_INVENTORY", String.valueOf(inventoryId), "UPDATED",
            "Fare changed from " + previousFare.toPlainString() + " to " + newFare.toPlainString());
        return new InventoryAdminDtos.FareUpdated(inventoryId, previousFare, newFare, "UPDATED");
    }

}

