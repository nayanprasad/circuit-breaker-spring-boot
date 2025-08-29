package com.cb.CircuitBreaker.controller;

import com.cb.CircuitBreaker.service.ExternalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@RestController
@RequestMapping("/api")
public class Controller {

    @Autowired
    private ExternalService externalService;

    
    @GetMapping("/external")
    public String callExternal() {
        return externalService.callExternalAPI();
    }
    
    @GetMapping("/status")
    public String getStatus() {
        return externalService.getCircuitBreakerStatus();
    }
}
