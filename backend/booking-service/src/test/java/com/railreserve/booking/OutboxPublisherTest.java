package com.railreserve.booking;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.apache.kafka.clients.producer.ProducerRecord;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {
    @Mock JdbcTemplate jdbc;
    @Mock KafkaTemplate<String, String> kafka;

    private OutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new OutboxPublisher(jdbc, kafka, "railreserve.booking.events");
    }

    @Test
    void doesNotCallKafkaWhenThereAreNoPendingOutboxEvents() {
        when(jdbc.query(anyString(), any(RowMapper.class))).thenReturn(List.of());

        publisher.publishBatch();

        verifyNoInteractions(kafka);
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void incrementsAttemptCountAndLeavesEventUnpublishedWhenKafkaSendFails() throws Exception {
        UUID eventId = UUID.randomUUID();
        byte[] eventIdBytes = uuidBytes(eventId);
        ResultSet resultSet = org.mockito.Mockito.mock(ResultSet.class);
        when(resultSet.getBytes("event_id")).thenReturn(eventIdBytes);
        when(resultSet.getString("aggregate_type")).thenReturn("Booking");
        when(resultSet.getString("aggregate_id")).thenReturn("booking-123");
        when(resultSet.getString("event_type")).thenReturn("BookingCreated");
        when(resultSet.getString("payload")).thenReturn("{\"bookingId\":\"booking-123\"}");
        doAnswer(invocation -> {
            RowMapper<?> mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(resultSet, 0));
        }).when(jdbc).query(anyString(), any(RowMapper.class));
        when(kafka.send(any(ProducerRecord.class))).thenReturn(
            CompletableFuture.<SendResult<String, String>>failedFuture(new IllegalStateException("broker unavailable")));

        assertDoesNotThrow(() -> publisher.publishBatch());

        verify(jdbc).update(eq("UPDATE outbox_events SET attempts=attempts+1 WHERE event_id=?"), eq(eventIdBytes));
        verify(jdbc, never()).update(eq("UPDATE outbox_events SET published_at=?, attempts=attempts+1 WHERE event_id=? AND published_at IS NULL"),
            any(), any());
    }

    private static byte[] uuidBytes(UUID uuid) {
        return ByteBuffer.allocate(16)
            .putLong(uuid.getMostSignificantBits())
            .putLong(uuid.getLeastSignificantBits())
            .array();
    }
}
