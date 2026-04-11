package com.filesync.server.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {
    @GetMapping("/health")
    public Map<String, String> getMap()
    {
        return Map.of("status", "Server is running");
    }
}
