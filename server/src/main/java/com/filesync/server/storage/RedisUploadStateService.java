package com.filesync.server.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class RedisUploadStateService
{
    private static final Logger log = LoggerFactory.getLogger(RedisUploadStateService.class);
    private final RedisTemplate<String, String> redisTemplate;

    public RedisUploadStateService(RedisTemplate<String, String> redisTemplate)
    {
        this.redisTemplate = redisTemplate;
    }

    public void setUploadId(String fileId, String uploadId)
    {
        log.debug("setUploadId: fileId={}, uploadId={}", fileId, uploadId);
        redisTemplate.opsForHash().put("upload:ids", fileId, uploadId);
        String check = (String) redisTemplate.opsForHash().get("upload:ids", fileId);
        log.debug("setUploadId verification: {}", check);
        redisTemplate.expire("upload:ids", 1, TimeUnit.HOURS);
    }

    public String getUploadId(String fileId)
    {
        String id = (String) redisTemplate.opsForHash().get("upload:ids", fileId);
        log.debug("getUploadId: fileId={} -> {}", fileId, id);
        return id;
    }

    public void removeUploadId(String fileId)
    {
        log.debug("removeUploadId: fileId={}", fileId);
        redisTemplate.opsForHash().delete("upload:ids", fileId);
    }

    public void addUploadedChunk(String fileId, int chunkIndex)
    {
        String key = "upload:parts:" + fileId;
        log.debug("addUploadedChunk: fileId={}, chunk={}", fileId, chunkIndex);
        redisTemplate.opsForSet().add(key, String.valueOf(chunkIndex));
        redisTemplate.expire(key, 1, TimeUnit.HOURS);
    }

    public Set<Integer> getUploadedChunks(String fileId)
    {
        String key = "upload:parts:" + fileId;
        Set<String> members = redisTemplate.opsForSet().members(key);
        log.debug("getUploadedChunks: fileId={} -> {}", fileId, members);
        Set<Integer> result = new HashSet<>();
        for (String s : members)
        {
            result.add(Integer.parseInt(s));
        }
        return result;
    }

    public void removeUploadedChunks(String fileId)
    {
        String key = "upload:parts:" + fileId;
        log.debug("removeUploadedChunks: fileId={}", fileId);
        redisTemplate.delete(key);
    }

    public void setPartETag(String fileId, int partNum, String etag)
    {
        String key = "upload:etags:" + fileId;
        log.debug("setPartETag: fileId={}, part={}", fileId, partNum);
        redisTemplate.opsForHash().put(key, String.valueOf(partNum), etag);
        redisTemplate.expire(key, 1, TimeUnit.HOURS);
    }

    public Map<Integer, String> getPartETags(String fileId)
    {
        String key = "upload:etags:" + fileId;
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
        Map<Integer, String> result = new HashMap<>();
        for (Map.Entry<Object, Object> e : entries.entrySet())
        {
            result.put(Integer.parseInt((String) e.getKey()), (String) e.getValue());
        }
        log.debug("getPartETags: fileId={} -> {} entries", fileId, result.size());
        return result;
    }

    public void removePartETags(String fileId)
    {
        String key = "upload:etags:" + fileId;
        log.debug("removePartETags: fileId={}", fileId);
        redisTemplate.delete(key);
    }

    public void cleanup(String fileId)
    {
        log.info("cleanup: fileId={}", fileId);
        removeUploadId(fileId);
        removePartETags(fileId);
        removeUploadedChunks(fileId);
    }

    public boolean tryLock(String fileId, long timeoutSeconds) {
        String lockKey = "lock:upload:" + fileId;
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "locked", Duration.ofSeconds(timeoutSeconds));
        return Boolean.TRUE.equals(acquired);
    }

    public void unlock(String fileId) {
        String lockKey = "lock:upload:" + fileId;
        redisTemplate.delete(lockKey);
    }
}