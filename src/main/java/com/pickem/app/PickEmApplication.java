package com.pickem.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling; // 1. Import this

@SpringBootApplication
@EnableScheduling // 2. Add this annotation to wake up the background timer
public class PickEmApplication {

    public static void main(String[] args) {
        SpringApplication.run(PickEmApplication.class, args);
    }
}