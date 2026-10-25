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
}
