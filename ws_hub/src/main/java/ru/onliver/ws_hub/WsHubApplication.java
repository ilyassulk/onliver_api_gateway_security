package ru.onliver.ws_hub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication
@EnableKafka
public class WsHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(WsHubApplication.class, args);
    }

}
