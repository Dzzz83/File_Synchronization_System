package com.filesync.server.conflict.strategy;

import com.filesync.common.dto.ConflictContextDto;

public interface ConflictStrategyInterface {
    String resolve(ConflictContextDto context);
}