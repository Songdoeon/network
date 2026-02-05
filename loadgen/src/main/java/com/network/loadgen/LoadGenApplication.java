package com.network.loadgen;

import com.network.loadgen.config.LoadGenProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(LoadGenProperties.class)
public class LoadGenApplication {

    public static void main(String[] args) {
        SpringApplication.run(LoadGenApplication.class, args);
    }
}
