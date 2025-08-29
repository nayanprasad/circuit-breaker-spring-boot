package com.cb.CircuitBreaker.service;

import org.springframework.stereotype.Service;

import java.util.Random;

@Service
public class ExternalService {

    private final CircuitBreaker circuitBreaker;
    private final Random random = new Random();

    public ExternalService(CircuitBreaker circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
    }

    public String callExternalAPI() {
        return circuitBreaker.execute(
                "external-service",
                this::simulateExternalCall,
                this::fallBackForExternalCall
        );
    }

    private String simulateExternalCall() {
        // Simulate random failures (30% chance of failure)
        if (random.nextDouble() < 0.3) {
            throw new RuntimeException("External service is down");
        }
        return "External service response: Success!";
    }

    public String fallBackForExternalCall() {
        return "External service response (fallback): Success!";
    }
}
