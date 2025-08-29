package com.cb.CircuitBreaker.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "circuit.breaker")
public class CircuitBreakerConfig {
    
    private double failureRateThreshold;
    private int minimumNumberOfCalls;
    private int slidingWindowSize;
    private int waitDurationInOpenState;
    private int permittedNumberOfCallsInHalfOpenState;
}
