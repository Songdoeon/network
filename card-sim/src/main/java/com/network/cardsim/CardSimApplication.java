package com.network.cardsim;

import com.network.cardsim.config.SimulatorProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(SimulatorProperties.class)
public class CardSimApplication {

    public static void main(String[] args) {
        SpringApplication.run(CardSimApplication.class, args);
    }
}
