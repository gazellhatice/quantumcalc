package com.quantumcalc.advanced;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.quantumcalc")
public class AdvancedMathApplication {
    public static void main(String[] args) {
        SpringApplication.run(AdvancedMathApplication.class, args);
    }
}
