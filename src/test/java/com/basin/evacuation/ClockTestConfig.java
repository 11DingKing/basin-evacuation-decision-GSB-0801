package com.basin.evacuation;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class ClockTestConfig {

    @Bean
    @Primary
    public MutableClock mutableClock() {
        return MutableClock.now();
    }
}
