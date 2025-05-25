package ru.onliver.ws_hub.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.Map;

@Controller
public class StatusController {

    private static final Logger logger = LoggerFactory.getLogger(StatusController.class);

    @Value("${spring.kafka.bootstrap-servers}")
    private String kafkaBootstrapServers;

    @GetMapping("/status")
    @ResponseBody
    public Map<String, Object> status() {
        Map<String, Object> status = new HashMap<>();
        status.put("status", "ok");
        status.put("service", "WebSocket Hub");
        status.put("kafka", kafkaBootstrapServers);
        
        logger.info("Status check: {}", status);
        
        return status;
    }
} 