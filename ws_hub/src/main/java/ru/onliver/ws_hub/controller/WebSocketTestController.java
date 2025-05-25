package ru.onliver.ws_hub.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.Map;

@Controller
public class WebSocketTestController {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketTestController.class);

    @GetMapping("/ws-test")
    @ResponseBody
    public Map<String, Object> testEndpoint() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "ok");
        response.put("message", "WebSocket endpoint is available");
        return response;
    }
} 