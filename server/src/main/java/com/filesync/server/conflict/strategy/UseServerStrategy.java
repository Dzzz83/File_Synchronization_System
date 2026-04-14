package com.filesync.server.conflict.strategy;

import com.filesync.common.dto.ConflictContextDto;
import org.springframework.stereotype.Component;

@Component("serverStrategy")
public class UseServerStrategy implements ConflictResolutionStrategy {
    @Override
    public String resolve(ConflictContextDto context) {
        return context.getServerContent();
    }
}