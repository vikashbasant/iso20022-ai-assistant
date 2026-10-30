package com.vikashbasant.paymentsai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class PaymentsAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentsAiApplication.class, args);
    }
}
