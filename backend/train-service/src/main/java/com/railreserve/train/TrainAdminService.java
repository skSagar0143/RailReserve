package com.railreserve.train;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TrainAdminService {
    private final JdbcTemplate jdbc;
    public TrainAdminService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public TrainAdminDtos.CreatedResource createStation(TrainAdminDtos.CreateStationRequest request) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO stations(code,name,city,active) VALUES(?,?,?,TRUE)", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, request.code().trim().toUpperCase(Locale.ROOT));
            ps.setString(2, request.name().trim());
            ps.setString(3, request.city().trim());
            return ps;
        }, key);
        return new TrainAdminDtos.CreatedResource(key.getKey().longValue(), "CREATED");
    }

    @Transactional
    public TrainAdminDtos.CreatedResource createTrain(TrainAdminDtos.CreateTrainRequest request) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO trains(train_number,name,active) VALUES(?,?,TRUE)", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, request.trainNumber().trim().toUpperCase(Locale.ROOT));
            ps.setString(2, request.name().trim());
            return ps;
        }, key);
        return new TrainAdminDtos.CreatedResource(key.getKey().longValue(), "CREATED");
    }

    @Transactional
    public TrainAdminDtos.CreatedResource addStop(long trainId, TrainAdminDtos.CreateStopRequest request) {
        if (request.arrivalTime() == null && request.departureTime() == null) {
            throw new IllegalArgumentException("A stop must have an arrival time, departure time, or both.");
        }
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO train_stops(train_id,station_id,stop_sequence,arrival_time,departure_time,day_offset) VALUES(?,?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, trainId);
            ps.setLong(2, request.stationId());
            ps.setInt(3, request.stopSequence());
            ps.setObject(4, request.arrivalTime() == null ? null : java.sql.Time.valueOf(request.arrivalTime()));
            ps.setObject(5, request.departureTime() == null ? null : java.sql.Time.valueOf(request.departureTime()));
            ps.setInt(6, request.dayOffset());
            return ps;
        }, key);
        return new TrainAdminDtos.CreatedResource(key.getKey().longValue(), "CREATED");
    }

    @Transactional
    public TrainAdminDtos.CreatedResource addServiceDate(long trainId, TrainAdminDtos.CreateServiceDateRequest request) {
        if (request.serviceDate().isBefore(java.time.LocalDate.now())) {
            throw new IllegalArgumentException("Service date cannot be in the past.");
        }
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO train_service_calendar(train_id,service_date,status) VALUES(?,?,'SCHEDULED')",
                Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, trainId);
            ps.setDate(2, java.sql.Date.valueOf(request.serviceDate()));
            return ps;
        }, key);
        return new TrainAdminDtos.CreatedResource(key.getKey().longValue(), "CREATED");
    }
}
