package com.threatsense;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ThreatsenseApplication {

    public static void main(String[] args) {
        SpringApplication.run(ThreatsenseApplication.class, args);
    }
}

