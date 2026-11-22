package com.vagcv.garage.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    /** Час через Clock, а не Instant.now(): так його легко підмінити в тестах (ЛР 2). */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
