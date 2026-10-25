package com.railreserve.train;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TrainAdminService {
    private static final Logger log = LoggerFactory.getLogger(TrainAdminService.class);
    private static final String CACHE_VERSION_KEY = "railreserve:train:cache-version";
    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    public TrainAdminService(JdbcTemplate jdbc, StringRedisTemplate redis) { this.jdbc = jdbc; this.redis = redis; }

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
        invalidateCacheAfterCommit();
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
        invalidateCacheAfterCommit();
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
        invalidateCacheAfterCommit();
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
        invalidateCacheAfterCommit();
        return new TrainAdminDtos.CreatedResource(key.getKey().longValue(), "CREATED");
    }
    private void invalidateCacheAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        redis.opsForValue().increment(CACHE_VERSION_KEY);
                    } catch (Exception ex) {
                        log.warn("Could not bump train cache version after admin update; cache entries will expire by TTL.");
                    }
                }
            });
        }
    }
}
