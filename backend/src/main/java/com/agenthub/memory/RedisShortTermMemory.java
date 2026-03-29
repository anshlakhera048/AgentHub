package com.agenthub.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConditionalOnProperty(name = "agenthub.memory.redis.enabled", havingValue = "true", matchIfMissing = true)
public class RedisShortTermMemory implements ShortTermMemory {

    private static final Logger log = LoggerFactory.getLogger(RedisShortTermMemory.class);
    private static final String KEY_PREFIX = "agenthub:memory:session:";

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;
    private final int maxHistoryLength;

    public RedisShortTermMemory(
            StringRedisTemplate redisTemplate,
            @Value("${agenthub.memory.short-term.ttl-minutes:60}") int ttlMinutes,
            @Value("${agenthub.memory.short-term.max-history:10}") int maxHistoryLength
    ) {
        this.redisTemplate = redisTemplate;
        this.ttl = Duration.ofMinutes(ttlMinutes);
        this.maxHistoryLength = maxHistoryLength;
    }

    @Override
    public void store(String sessionId, String content) {
        String key = KEY_PREFIX + sessionId;
        try {
            // Append to a Redis list (FIFO)
            redisTemplate.opsForList().rightPush(key, content);

            // Trim to max history length
            Long size = redisTemplate.opsForList().size(key);
            if (size != null && size > maxHistoryLength) {
                redisTemplate.opsForList().trim(key, size - maxHistoryLength, -1);
            }

            // Refresh TTL
            redisTemplate.expire(key, ttl);
            log.debug("Stored short-term memory for session '{}', entries: {}", sessionId, size);
        } catch (Exception e) {
            log.warn("Failed to store in Redis for session '{}': {}", sessionId, e.getMessage());
        }
    }

    @Override
    public String retrieve(String sessionId) {
        String key = KEY_PREFIX + sessionId;
        try {
            var entries = redisTemplate.opsForList().range(key, 0, -1);
            if (entries == null || entries.isEmpty()) {
                return "";
            }
            return String.join("\n", entries);
        } catch (Exception e) {
            log.warn("Failed to retrieve from Redis for session '{}': {}", sessionId, e.getMessage());
            return "";
        }
    }

    @Override
    public void clear(String sessionId) {
        String key = KEY_PREFIX + sessionId;
        try {
            redisTemplate.delete(key);
            log.debug("Cleared short-term memory for session '{}'", sessionId);
        } catch (Exception e) {
            log.warn("Failed to clear Redis for session '{}': {}", sessionId, e.getMessage());
        }
    }
}
