package com.sport;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BootApplication {
    public static void main(String[] args) {
        //启动
        SpringApplication.run(BootApplication.class);
    }
}