package ru.onliver.ws_hub.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.MessageChannels;
import org.springframework.integration.handler.LoggingHandler;
import org.springframework.integration.kafka.dsl.Kafka;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.support.DefaultKafkaHeaderMapper;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.support.MessageBuilder;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableIntegration
public class IntegrationConfig {

    private static final Logger logger = LoggerFactory.getLogger(IntegrationConfig.class);

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String consumerGroupId;

    /* ---------- каналы ---------- */

    @Bean
    public MessageChannel wsInboundChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageChannel chatKafkaChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageChannel playlistKafkaChannel() {
        return new DirectChannel();
    }

    @Bean
    public DefaultKafkaHeaderMapper headerMapper() {
        DefaultKafkaHeaderMapper mapper = new DefaultKafkaHeaderMapper();
        // уже есть:
        mapper.setRawMappedHeaders(Map.of(
                SimpMessageHeaderAccessor.DESTINATION_HEADER,                    true,
                SimpMessageHeaderAccessor.SESSION_ID_HEADER,      true,
                SimpMessageHeaderAccessor.SESSION_ATTRIBUTES,     true
        ));
        mapper.addTrustedPackages("*");
        return mapper;
    }


    /* ---------- 1. WebSocket inbound ---------- */

    @Bean
    public IntegrationFlow webSocketInbound() {
        return IntegrationFlow.from("clientInboundChannel")
                .log(LoggingHandler.Level.INFO,
                        "ru.onliver.ws_hub.integration.inbound",
                        m -> "Получено WebSocket сообщение: " + m)
                .transform(Message.class, msg -> {
                    // Получаем destination из STOMP сообщения
                    String destination = SimpMessageHeaderAccessor.getDestination(msg.getHeaders());
                    // Преобразуем payload в String если это byte[]
                    Object payload = msg.getPayload();
                    String stringPayload;
                    if (payload instanceof byte[]) {
                        stringPayload = new String((byte[]) payload, StandardCharsets.UTF_8);
                    } else {
                        stringPayload = payload.toString();
                    }
                    
                    // Создаем новое сообщение с сохранением destination
                    Message message = MessageBuilder.withPayload(stringPayload).copyHeaders(msg.getHeaders())
                            .build();
                    return message;
                })
                .route(Message.class,
                        msg -> {
                            String path = msg.getHeaders().get("simpDestination", String.class);
                            if (path != null && path.startsWith("/app/chat/")) return "chat";
                            if (path != null && path.startsWith("/app/playlist/")) return "playlist";
                            return "other";
                        },
                        mapping -> mapping
                                .channelMapping("chat", chatKafkaChannel())
                                .channelMapping("playlist", playlistKafkaChannel())
                                .channelMapping("other", "nullChannel")
                )
                .get();
    }

    /* ---------- 3. Kafka outbound ---------- */

    @Bean
    public IntegrationFlow chatKafkaOutbound(KafkaTemplate<String, String> template) {
        return IntegrationFlow.from(chatKafkaChannel())
                .log(LoggingHandler.Level.INFO, "ru.onliver.ws_hub.integration.chat", 
                     message -> "Отправка в Kafka (chat): " + message)
                .transform(Message.class, msg -> {
                    Object payload = msg.getPayload();
                    if (payload instanceof String) {
                        return payload;
                    } else if (payload instanceof byte[]) {
                        return new String((byte[]) payload, StandardCharsets.UTF_8);
                    } else {
                        return payload.toString();
                    }
                })
                .handle(Kafka.outboundChannelAdapter(template)
                        .headerMapper(headerMapper())
                        .topic("chat-commands"))
                .get();
    }

    @Bean
    public IntegrationFlow playlistKafkaOutbound(KafkaTemplate<String, String> template) {
        return IntegrationFlow.from(playlistKafkaChannel())
                .log(LoggingHandler.Level.INFO, "ru.onliver.ws_hub.integration.playlist", 
                     message -> "Отправка в Kafka (playlist): " + message)
                .transform(Message.class, msg -> {
                    Object payload = msg.getPayload();
                    if (payload instanceof String) {
                        return payload;
                    } else if (payload instanceof byte[]) {
                        return new String((byte[]) payload, StandardCharsets.UTF_8);
                    } else {
                        return payload.toString();
                    }
                })
                .handle(Kafka.outboundChannelAdapter(template)
                        .headerMapper(headerMapper())
                        .topic("playlist-commands"))
                .get();
    }

    /* ---------- 4. Kafka inbound → WebSocket ---------- */

    @Bean
    public IntegrationFlow liveEventsInboundToStomp(ConsumerFactory<String, String> cf, SimpMessagingTemplate messagingTemplate) {
        ContainerProperties props = new ContainerProperties("live-events");
        KafkaMessageListenerContainer<String, String> container =
                new KafkaMessageListenerContainer<>(cf, props);

        return IntegrationFlow.from(Kafka.messageDrivenChannelAdapter(container))
                .log(LoggingHandler.Level.INFO, "ru.onliver.ws_hub.integration.live-events",
                     message -> "Получено из Kafka: " + message)
                .handle(message -> {
                    try {
                        String destination = (String) message.getHeaders().get("destination");
                        
                        if (destination == null) {
                            destination = "/topic/events";
                            logger.info("Используется destination по умолчанию: {}", destination);
                        }
                        
                        logger.info("Отправка клиентам: destination={}, payload={}", 
                                destination, message.getPayload());
                        
                        messagingTemplate.convertAndSend(destination, message.getPayload());
                    } catch (Exception e) {
                        logger.error("Ошибка обработки сообщения из Kafka: {}", e.getMessage(), e);
                    }
                })
                .get();
    }

    /* ---------- Kafka factories ---------- */

    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate(ProducerFactory<String, String> pf) {
        return new KafkaTemplate<>(pf);
    }

    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new DefaultKafkaConsumerFactory<>(props);
    }
}
