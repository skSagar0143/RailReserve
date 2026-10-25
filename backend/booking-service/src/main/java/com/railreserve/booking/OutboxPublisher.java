package com.railreserve.booking;

import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private final JdbcTemplate jdbc;
    private final KafkaTemplate<String, String> kafka;
    private final String topic;

    public OutboxPublisher(JdbcTemplate jdbc, KafkaTemplate<String, String> kafka,
                           @Value("${railreserve.kafka.booking-topic:railreserve.booking.events}") String topic) {
        this.jdbc = jdbc;
        this.kafka = kafka;
        this.topic = topic;
    }

    @Scheduled(fixedDelayString = "${railreserve.outbox.poll-interval-ms:1000}")
    @Transactional
    public void publishBatch() {
        List<OutboxEvent> events = jdbc.query(
            "SELECT event_id, aggregate_type, aggregate_id, event_type, payload FROM outbox_events WHERE published_at IS NULL ORDER BY created_at LIMIT 50 FOR UPDATE SKIP LOCKED",
            (rs, row) -> new OutboxEvent(uuidFromBytes(rs.getBytes("event_id")), rs.getString("aggregate_type"),
                rs.getString("aggregate_id"), rs.getString("event_type"), rs.getString("payload")));
        for (OutboxEvent event : events) {
            try {
                ProducerRecord<String, String> record = new ProducerRecord<>(topic, event.eventId().toString(), event.payload());
                record.headers().add(new RecordHeader("event_type", event.eventType().getBytes(StandardCharsets.UTF_8)));
                record.headers().add(new RecordHeader("aggregate_type", event.aggregateType().getBytes(StandardCharsets.UTF_8)));
                record.headers().add(new RecordHeader("aggregate_id", event.aggregateId().getBytes(StandardCharsets.UTF_8)));
                kafka.send(record).get(10, TimeUnit.SECONDS);
                jdbc.update("UPDATE outbox_events SET published_at=?, attempts=attempts+1 WHERE event_id=? AND published_at IS NULL",
                    java.sql.Timestamp.from(Instant.now()), uuidBytes(event.eventId()));
            } catch (Exception ex) {
                jdbc.update("UPDATE outbox_events SET attempts=attempts+1 WHERE event_id=?", uuidBytes(event.eventId()));
                log.warn("Outbox publish failed for eventId={} type={}; it will be retried", event.eventId(), event.eventType());
            }
        }
    }

    private static byte[] uuidBytes(UUID uuid) {
        return ByteBuffer.allocate(16).putLong(uuid.getMostSignificantBits()).putLong(uuid.getLeastSignificantBits()).array();
    }
    private static UUID uuidFromBytes(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }
    private record OutboxEvent(UUID eventId, String aggregateType, String aggregateId, String eventType, String payload) {}
}
