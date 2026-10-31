package com.gemform.be.echoes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EchoesApplication {

    public static void main(String[] args) {
        SpringApplication.run(EchoesApplication.class, args);
    }
}
