package com.cb.CircuitBreaker.service;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

@Slf4j
public class CircuitBreaker {

    public enum State {
        CLOSED,    // Normal operation
        OPEN,      // Circuit is open, calls are failing fast
        HALF_OPEN  // Testing if service is back to normal
    }

    private static class CallResult {
        private final boolean success;
        private final LocalDateTime timestamp;

        public CallResult(boolean success) {
            this.success = success;
            this.timestamp = LocalDateTime.now();
        }

        public boolean isSuccess() {
            return success;
        }

        public LocalDateTime getTimestamp() {
            return timestamp;
        }
    }

    // Configuration parameters
    private final double failureRateThreshold;
    private final int minimumNumberOfCalls;
    private final int slidingWindowSize;
    private final int waitDurationInOpenStateSeconds;
    private final int permittedNumberOfCallsInHalfOpenState;

    // State management
    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final ConcurrentLinkedQueue<CallResult> callResults = new ConcurrentLinkedQueue<>();
    private final AtomicInteger halfOpenCallCount = new AtomicInteger(0);
    private volatile LocalDateTime lastOpenTime;

    public CircuitBreaker(double failureRateThreshold,
                          int minimumNumberOfCalls,
                          int slidingWindowSize,
                          int waitDurationInOpenStateSeconds,
                          int permittedNumberOfCallsInHalfOpenState) {
        this.failureRateThreshold = failureRateThreshold;
        this.minimumNumberOfCalls = minimumNumberOfCalls;
        this.slidingWindowSize = slidingWindowSize;
        this.waitDurationInOpenStateSeconds = waitDurationInOpenStateSeconds;
        this.permittedNumberOfCallsInHalfOpenState = permittedNumberOfCallsInHalfOpenState;
    }

    public <T> T execute(Supplier<T> operation, Supplier<T> fallback) {
        if (operation == null) {
            throw new IllegalArgumentException("Operation cannot be null");
        }
        if (fallback == null) {
            throw new IllegalArgumentException("Fallback cannot be null");
        }

        State currentState = getCurrentStateAndTransition();

        if (currentState == State.OPEN) {
            log.debug("Circuit breaker is OPEN - executing fallback");
            return fallback.get();
        }

        if (currentState == State.HALF_OPEN && !canExecuteInHalfOpen()) {
            log.debug("Circuit breaker is HALF_OPEN - maximum calls exceeded, executing fallback");
            return fallback.get();
        }

        if (currentState == State.HALF_OPEN) {
            halfOpenCallCount.incrementAndGet();
        }

        try {
            T result = operation.get();
            onSuccess();
            return result;
        } catch (Exception e) {
            log.debug("Operation failed: {}", e.getMessage());
            onFailure();
            return fallback.get();
        }
    }

    private State getCurrentStateAndTransition() {
        State currentState = state.get();

        if (currentState == State.OPEN && shouldAttemptReset()) {
            if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                halfOpenCallCount.set(0);
                log.info("Circuit breaker transitioned from OPEN to HALF_OPEN");
                return State.HALF_OPEN;
            }
        }

        return state.get();
    }

    private boolean canExecuteInHalfOpen() {
        return halfOpenCallCount.get() < permittedNumberOfCallsInHalfOpenState;
    }

    private void onSuccess() {
        addCallResult(true);

        if (state.get() == State.HALF_OPEN) {
            state.set(State.CLOSED);
            halfOpenCallCount.set(0);
            log.info("Circuit breaker transitioned from HALF_OPEN to CLOSED after successful call");
        }
    }

    private void onFailure() {
        addCallResult(false);

        State currentState = state.get();
        if (currentState == State.HALF_OPEN) {
            openCircuit();
            log.info("Circuit breaker transitioned from HALF_OPEN to OPEN after failed call");
        } else if (currentState == State.CLOSED && shouldOpenCircuit()) {
            openCircuit();
            log.warn("Circuit breaker transitioned from CLOSED to OPEN - failure rate threshold exceeded");
        }
    }

    private void addCallResult(boolean success) {
        callResults.offer(new CallResult(success));

        // Maintain sliding window size
        while (callResults.size() > slidingWindowSize) {
            callResults.poll();
        }
    }

    private boolean shouldOpenCircuit() {
        int currentCallCount = callResults.size();
        if (currentCallCount < minimumNumberOfCalls) {
            return false;
        }

        long failedCalls = callResults.stream()
                .mapToLong(result -> result.isSuccess() ? 0 : 1)
                .sum();

        double currentFailureRate = (double) failedCalls / currentCallCount * 100;
        return currentFailureRate >= failureRateThreshold;
    }

    private void openCircuit() {
        state.set(State.OPEN);
        lastOpenTime = LocalDateTime.now();
        halfOpenCallCount.set(0);
    }

    private boolean shouldAttemptReset() {
        return lastOpenTime != null &&
                LocalDateTime.now().isAfter(lastOpenTime.plusSeconds(waitDurationInOpenStateSeconds));
    }


    public State getState() {
        return state.get();
    }

    public int getCallCount() {
        return callResults.size();
    }

    public double getCurrentFailureRate() {
        int currentCallCount = callResults.size();
        if (currentCallCount == 0) {
            return 0.0;
        }

        long failedCalls = callResults.stream()
                .mapToLong(result -> result.isSuccess() ? 0 : 1)
                .sum();

        return (double) failedCalls / currentCallCount * 100;
    }

    public int getHalfOpenCallCount() {
        return halfOpenCallCount.get();
    }

    public LocalDateTime getLastOpenTime() {
        return lastOpenTime;
    }
}