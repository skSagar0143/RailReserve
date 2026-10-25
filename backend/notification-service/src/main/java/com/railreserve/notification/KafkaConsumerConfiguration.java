package com.railreserve.notification;

import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConsumerConfiguration {
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate,
        @Value("${railreserve.kafka.booking-topic:railreserve.booking.events}") String topic) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
            (record, exception) -> new org.apache.kafka.common.TopicPartition(topic + ".DLT", record.partition()));
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 5L));
        handler.addNotRetryableExceptions(IllegalArgumentException.class);
        return handler;
    }

    @Bean
    NewTopic bookingEventsDeadLetterTopic(
        @Value("${railreserve.kafka.booking-topic:railreserve.booking.events}") String topic,
        @Value("${railreserve.kafka.booking-topic-partitions:6}") int partitions,
        @Value("${railreserve.kafka.booking-topic-replication-factor:1}") short replicationFactor) {
        return TopicBuilder.name(topic + ".DLT").partitions(partitions).replicas(replicationFactor).build();
    }
}
