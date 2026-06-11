package com.filesync.server.conflict.strategy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class ConflictStrategyFactory {
    @Autowired
    private ApplicationContext context;

    public ConflictStrategyInterface getStrategy(String type) {
        switch (type) {
            case "server":
                return context.getBean("serverStrategy", ConflictStrategyInterface.class);
            case "user":
                return context.getBean("userStrategy", ConflictStrategyInterface.class);
            default:
                throw new IllegalArgumentException("Unknown strategy: " + type);
        }
    }
}