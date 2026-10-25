package com.railreserve.booking;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfiguration {
    @Bean
    NewTopic bookingEventsTopic(
        @Value("${railreserve.kafka.booking-topic:railreserve.booking.events}") String topic,
        @Value("${railreserve.kafka.booking-topic-partitions:6}") int partitions,
        @Value("${railreserve.kafka.booking-topic-replication-factor:1}") short replicationFactor) {
        return TopicBuilder.name(topic).partitions(partitions).replicas(replicationFactor).build();
    }
}
