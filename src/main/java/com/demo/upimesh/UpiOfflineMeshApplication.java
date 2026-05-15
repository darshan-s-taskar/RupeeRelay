package com.demo.upimesh;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class UpiOfflineMeshApplication {

    public static void main(String[] args) {
        SpringApplication.run(UpiOfflineMeshApplication.class, args);
    }
}
