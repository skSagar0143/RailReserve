package com.railreserve.train;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

@Service
public class TrainSearchService {
    private static final Logger log = LoggerFactory.getLogger(TrainSearchService.class);
    private static final String CACHE_VERSION_KEY = "railreserve:train:cache-version";
    private static final Duration CACHE_TTL = Duration.ofSeconds(30);
    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public TrainSearchService(JdbcTemplate jdbc, StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<TrainSearchDtos.TrainSearchResult> search(String fromCode, String toCode, LocalDate date) {
        String from = normalizeCode(fromCode);
        String to = normalizeCode(toCode);
        if (from.equals(to)) throw new IllegalArgumentException("Departure and destination stations must be different.");
        if (date.isBefore(LocalDate.now(ZoneId.of("Asia/Kolkata")))) {
            throw new IllegalArgumentException("Journey date cannot be in the past.");
        }

        String version;
        try {
            version = redis.opsForValue().get(CACHE_VERSION_KEY);
            if (version == null) {
                redis.opsForValue().setIfAbsent(CACHE_VERSION_KEY, "0");
                version = redis.opsForValue().get(CACHE_VERSION_KEY);
            }
            if (version == null) version = "0";
        } catch (Exception ex) {
            log.debug("Train search cache version unavailable; using database", ex);
            return queryDatabase(from, to, date);
        }

        String cacheKey = "railreserve:train-search:v1:" + version + ":" + from + ":" + to + ":" + date;
        try {
            String cached = redis.opsForValue().get(cacheKey);
            if (cached != null) return Arrays.asList(objectMapper.readValue(cached, TrainSearchDtos.TrainSearchResult[].class));
        } catch (Exception ex) {
            log.debug("Train search cache read failed; falling back to database");
        }

        List<TrainSearchDtos.TrainSearchResult> results = queryDatabase(from, to, date);
        try {
            redis.opsForValue().set(cacheKey, objectMapper.writeValueAsString(results), CACHE_TTL);
        } catch (Exception ex) {
            log.debug("Train search cache write failed; serving database results");
        }
        return results;
    }

    private List<TrainSearchDtos.TrainSearchResult> queryDatabase(String from, String to, LocalDate date) {
        return jdbc.query("""
            SELECT t.id AS train_id, t.train_number, t.name AS train_name,
                   sf.code AS from_code, sf.name AS from_station,
                   st.code AS to_code, st.name AS to_station,
                   sfstop.departure_time, sfstop.day_offset AS departure_day,
                   ststop.arrival_time, ststop.day_offset AS arrival_day
            FROM trains t
            JOIN train_stops sfstop ON sfstop.train_id=t.id
            JOIN stations sf ON sf.id=sfstop.station_id AND sf.active=TRUE
            JOIN train_stops ststop ON ststop.train_id=t.id AND ststop.stop_sequence>sfstop.stop_sequence
            JOIN stations st ON st.id=ststop.station_id AND st.active=TRUE
            JOIN train_service_calendar cal ON cal.train_id=t.id AND cal.service_date=? AND cal.status='SCHEDULED'
            WHERE t.active=TRUE AND UPPER(sf.code)=? AND UPPER(st.code)=?
              AND sfstop.departure_time IS NOT NULL AND ststop.arrival_time IS NOT NULL
            ORDER BY sfstop.departure_time, t.train_number
            LIMIT 100
            """, (rs, row) -> {
                LocalDateTime departure = LocalDateTime.of(date.plusDays(rs.getInt("departure_day")), rs.getTime("departure_time").toLocalTime());
                LocalDateTime arrival = LocalDateTime.of(date.plusDays(rs.getInt("arrival_day")), rs.getTime("arrival_time").toLocalTime());
                long minutes = java.time.Duration.between(departure, arrival).toMinutes();
                return new TrainSearchDtos.TrainSearchResult(rs.getLong("train_id"), rs.getString("train_number"),
                    rs.getString("train_name"), rs.getString("from_code"), rs.getString("from_station"),
                    rs.getString("to_code"), rs.getString("to_station"), departure, arrival, minutes);
            }, java.sql.Date.valueOf(date), from, to).stream()
            .filter(train -> train.durationMinutes() > 0 && train.durationMinutes() <= 72 * 60L)
            .toList();
    }

    private static String normalizeCode(String code) {
        if (code == null || !code.matches("[A-Za-z0-9]{2,10}")) {
            throw new IllegalArgumentException("Station codes must contain 2 to 10 letters or digits.");
        }
        return code.toUpperCase(java.util.Locale.ROOT);
    }
}
