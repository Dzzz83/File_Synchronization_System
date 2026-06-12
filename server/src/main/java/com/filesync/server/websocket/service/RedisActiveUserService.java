package com.filesync.server.websocket.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class RedisActiveUserService implements ActiveUserService {

    private static final String REDIS_ACTIVE_ZSET_PREFIX = "folder:active:zset:";
    private static final long STALE_TIMEOUT_SECONDS = 60; // remove users inactive for > 60 seconds

    private final StringRedisTemplate redisTemplate;

    public RedisActiveUserService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    private String getZSetKey(UUID folderId) {
        return REDIS_ACTIVE_ZSET_PREFIX + folderId.toString();
    }

    @Override
    public void userJoined(UUID folderId, String username) {
        String key = getZSetKey(folderId);
        long now = Instant.now().toEpochMilli();
        // Add user with current timestamp as score
        redisTemplate.opsForZSet().add(key, username, now);
        // Optionally expire the whole key if no users (cleanup)
        redisTemplate.expire(key, STALE_TIMEOUT_SECONDS + 10, TimeUnit.SECONDS);
    }

    @Override
    public void userLeft(UUID folderId, String username) {
        String key = getZSetKey(folderId);
        redisTemplate.opsForZSet().remove(key, username);
        // If set becomes empty, let it expire naturally (or delete)
    }

    @Override
    public Set<String> getActiveUsers(UUID folderId) {
        String key = getZSetKey(folderId);
        // Remove stale entries first (optional, but scheduled task does it)
        // Return all members (scores ignored)
        Set<String> members = redisTemplate.opsForZSet().reverseRange(key, 0, -1);
        return members != null ? members : Collections.emptySet();
    }

    // Scheduled cleanup: run every 30 seconds
    @Scheduled(fixedDelay = 30000, initialDelay = 5000)
    public void cleanStaleActiveUsers() {
        // Scan for all keys matching the prefix
        Set<String> keys = redisTemplate.keys(REDIS_ACTIVE_ZSET_PREFIX + "*");
        if (keys == null || keys.isEmpty()) return;
        long staleThreshold = Instant.now().toEpochMilli() - (STALE_TIMEOUT_SECONDS * 1000);
        for (String key : keys) {
            // Remove members with score less than staleThreshold
            Long removed = redisTemplate.opsForZSet().removeRangeByScore(key, Double.NEGATIVE_INFINITY, staleThreshold);
            if (removed != null && removed > 0) {
                System.out.println("Cleaned " + removed + " stale active users from " + key);
            }
            // If set becomes empty, delete key to save space
            Long size = redisTemplate.opsForZSet().size(key);
            if (size != null && size == 0) {
                redisTemplate.delete(key);
            }
        }
    }
}