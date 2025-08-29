package com.cb.CircuitBreaker.service;

import com.cb.CircuitBreaker.config.CircuitBreakerConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Random;

@Service
public class ExternalService {
    
    private final CircuitBreaker circuitBreaker;
    private final Random random = new Random();
    
    @Autowired
    public ExternalService(CircuitBreakerConfig config) {
        // Configure circuit breaker using properties
        this.circuitBreaker = new CircuitBreaker(
            config.getFailureRateThreshold(),
            config.getMinimumNumberOfCalls(),
            config.getSlidingWindowSize(),
            config.getWaitDurationInOpenState(),
            config.getPermittedNumberOfCallsInHalfOpenState()
        );
    }
    
    public String callExternalAPI() {
        try {
            return circuitBreaker.execute(this::simulateExternalCall, this::fallBackForExternalCall);
        } catch (Exception e) {
            return "Service unavailable - Circuit breaker is protecting the system: " + e.getMessage();
        }
    }
    
    private String simulateExternalCall() {
        // Simulate random failures (50% chance of failure)
        if (random.nextDouble() < 0.5) {
            throw new RuntimeException("External service is down");
        }
        
        // Simulate some processing time
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        return "External service response: Success!";
    }

    public String fallBackForExternalCall() {
        return "External service response (fallback): Success!";
    }

    public String getCircuitBreakerStatus() {
        return String.format("Circuit Breaker State: %s, Call Count: %d, Failure Rate: %.2f%%, Half-Open Calls: %d",
                circuitBreaker.getState(),
                circuitBreaker.getCallCount(),
                circuitBreaker.getCurrentFailureRate(),
                circuitBreaker.getHalfOpenCallCount());
    }
}
