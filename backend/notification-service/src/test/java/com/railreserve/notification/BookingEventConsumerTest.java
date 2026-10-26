package com.railreserve.notification;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class BookingEventConsumerTest {
    private JdbcTemplate jdbc;
    private BookingEventConsumer consumer;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        consumer = new BookingEventConsumer(jdbc);
    }

    @Test
    void persistsNewEventAndCreatesDurableNotificationJob() {
        when(jdbc.update(contains("INSERT IGNORE INTO notification_inbox"), any(), any(), any(), any(), any()))
            .thenReturn(1);
        consumer.consume(validRecord());

        verify(jdbc).update(contains("INSERT IGNORE INTO notification_inbox"),
            eq("event-123"), eq("BookingPendingPayment"), eq("Booking"), eq("booking-456"), eq("{\"bookingId\":\"booking-456\"}"));
        verify(jdbc).update(contains("INSERT INTO notification_jobs"), any(), eq("event-123"), eq("EMAIL"), eq("WAITING_FOR_RECIPIENT"));
    }

    @Test
    void ignoresDuplicateEventWithoutCreatingAnotherJob() {
        when(jdbc.update(contains("INSERT IGNORE INTO notification_inbox"), any(), any(), any(), any(), any()))
            .thenReturn(0);
        consumer.consume(validRecord());

        verify(jdbc, never()).update(contains("INSERT INTO notification_jobs"), any(), any(), any(), any());
    }

    @Test
    void rejectsEventWithoutRequiredMetadata() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("railreserve.booking.events", 0, 1L, "event-123", "{}");
        assertThrows(IllegalArgumentException.class, () -> consumer.consume(record));
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsEventWithoutKeyOrPayload() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("railreserve.booking.events", 0, 1L, null, "{}");
        assertThrows(IllegalArgumentException.class, () -> consumer.consume(record));
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsBlankEventKeyOrPayloadBeforeDatabaseWork() {
        ConsumerRecord<String, String> blankKey = validRecord();
        ConsumerRecord<String, String> blankPayload = new ConsumerRecord<>("railreserve.booking.events", 0, 2L, "event-124", "  ");
        assertThrows(IllegalArgumentException.class, () -> consumer.consume(blankKey.withKey(" ")));
        assertThrows(IllegalArgumentException.class, () -> consumer.consume(blankPayload));
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsBlankMetadataHeadersBeforeDatabaseWork() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("railreserve.booking.events", 0, 3L, "event-125", "{}");
        record.headers().add(new RecordHeader("event_type", "   ".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        record.headers().add(new RecordHeader("aggregate_type", "Booking".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        record.headers().add(new RecordHeader("aggregate_id", "booking-456".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertThrows(IllegalArgumentException.class, () -> consumer.consume(record));
        verifyNoInteractions(jdbc);
    }

    private static ConsumerRecord<String, String> validRecord() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("railreserve.booking.events", 0, 1L,
            "event-123", "{\"bookingId\":\"booking-456\"}");
        record.headers().add(new RecordHeader("event_type", "BookingPendingPayment".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        record.headers().add(new RecordHeader("aggregate_type", "Booking".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        record.headers().add(new RecordHeader("aggregate_id", "booking-456".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        return record;
    }
}
