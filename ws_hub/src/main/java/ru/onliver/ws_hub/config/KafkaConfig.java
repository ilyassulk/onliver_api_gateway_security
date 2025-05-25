package ru.onliver.ws_hub.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Конфигурация Kafka для обработки событий комнат и трансляций
 */
@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic chatCommandsTopic() {
        return TopicBuilder.name("chat-commands").build();
    }

    @Bean
    public NewTopic playlistCommandsTopic() {
        return TopicBuilder.name("playlist-commands").build();
    }

    @Bean
    public NewTopic liveEventsTopic() {
        return TopicBuilder.name("live-events").build();
    }

}
