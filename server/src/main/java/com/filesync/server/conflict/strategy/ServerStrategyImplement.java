package com.filesync.server.conflict.strategy;

import com.filesync.common.dto.ConflictContextDto;
import org.springframework.stereotype.Component;

@Component("serverStrategy")
public class ServerStrategyImplement implements ConflictStrategyInterface {
    @Override
    public String resolve(ConflictContextDto context) {
        return context.getServerContent();
    }
}