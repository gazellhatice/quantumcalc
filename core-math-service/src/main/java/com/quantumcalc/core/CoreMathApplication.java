package com.quantumcalc.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// "com.quantumcalc" taranır: common modülündeki producer & tracing config'i de yüklenir.
@SpringBootApplication(scanBasePackages = "com.quantumcalc")
public class CoreMathApplication {
    public static void main(String[] args) {
        SpringApplication.run(CoreMathApplication.class, args);
    }
}
