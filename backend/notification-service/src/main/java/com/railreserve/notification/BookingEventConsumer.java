package com.railreserve.notification;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BookingEventConsumer {
    private static final Logger log = LoggerFactory.getLogger(BookingEventConsumer.class);
    private final JdbcTemplate jdbc;

    public BookingEventConsumer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @KafkaListener(topics = "${railreserve.kafka.booking-topic:railreserve.booking.events}")
    @Transactional
    public void consume(ConsumerRecord<String, String> record) {
        if (record.key() == null || record.value() == null) {
            throw new IllegalArgumentException("Booking event must have an event ID key and JSON payload.");
        }
        String eventType = header(record, "event_type");
        String aggregateType = header(record, "aggregate_type");
        String aggregateId = header(record, "aggregate_id");
        if (eventType == null || aggregateType == null || aggregateId == null) {
            throw new IllegalArgumentException("Booking event is missing required metadata headers.");
        }
        int inserted = jdbc.update(
            "INSERT IGNORE INTO notification_inbox(event_id,event_type,aggregate_type,aggregate_id,payload) VALUES(?,?,?,?,?)",
            record.key(), eventType, aggregateType, aggregateId, record.value());
        if (inserted == 1) {
            jdbc.update("INSERT INTO notification_jobs(id,event_id,channel,status) VALUES(?,?,?,?)",
                uuidBytes(UUID.randomUUID()), record.key(), "EMAIL", "WAITING_FOR_RECIPIENT");
            log.info("Accepted booking event into durable notification inbox eventId={} eventType={}", record.key(), eventType);
        } else {
            log.debug("Ignoring duplicate booking event eventId={}", record.key());
        }
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    private static byte[] uuidBytes(UUID uuid) {
        return ByteBuffer.allocate(16).putLong(uuid.getMostSignificantBits()).putLong(uuid.getLeastSignificantBits()).array();
    }
}
