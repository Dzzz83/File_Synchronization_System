package com.filesync.server.conflict.strategy;

import com.filesync.common.dto.ConflictContextDto;

public interface ConflictResolutionStrategy {
    String resolve(ConflictContextDto context);
}