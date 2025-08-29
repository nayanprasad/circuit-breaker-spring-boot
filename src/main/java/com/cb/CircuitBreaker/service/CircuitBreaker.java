package com.cb.CircuitBreaker.service;

import com.cb.CircuitBreaker.config.CircuitBreakerConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;


@Service
@Slf4j
public class CircuitBreaker {

    private final ConcurrentHashMap<String, CircuitBreakerState> circuitBreakers = new ConcurrentHashMap<>();
    private final CircuitBreakerConfig config;

    public CircuitBreaker(CircuitBreakerConfig config) {
        this.config = config;
        log.info("CircuitBreaker service initialized");
    }

    public <T> T execute(String id, Supplier<T> operation, Supplier<T> fallback) {
        CircuitBreakerState state = getOrCreateState(id);
        CircuitBreakerState.State currentState = state.getCurrentStateAndTransition();

        if (currentState == CircuitBreakerState.State.OPEN) {
            log.debug("Circuit breaker [{}] is OPEN - executing fallback", id);
            return fallback.get();
        }

        if (currentState == CircuitBreakerState.State.HALF_OPEN && !state.canExecuteInHalfOpen()) {
            log.debug("Circuit breaker [{}] is HALF_OPEN - maximum calls exceeded, executing fallback", id);
            return fallback.get();
        }

        if (currentState == CircuitBreakerState.State.HALF_OPEN) {
            state.incrementHalfOpenCallCount();
        }

        try {
            T result = operation.get();
            state.recordSuccess();
            log.debug("Circuit breaker [{}] - operation succeeded", id);
            return result;
        } catch (Exception e) {
            log.debug("Circuit breaker [{}] - operation failed: {}", id, e.getMessage());
            state.recordFailure();
            return fallback.get();
        }
    }

    private CircuitBreakerState getOrCreateState(String id) {
        return circuitBreakers.computeIfAbsent(id, cbId -> {
            log.info("Creating circuit breaker [{}] with default configuration", id);
            return new CircuitBreakerState(
                    config.getFailureRateThreshold(),
                    config.getMinimumNumberOfCalls(),
                    config.getSlidingWindowSize(),
                    config.getWaitDurationInOpenState(),
                    config.getPermittedNumberOfCallsInHalfOpenState()
            );
        });
    }

}