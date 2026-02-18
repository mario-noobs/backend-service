package com.mario.backend.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Date;

@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private static final String BLACKLIST_PREFIX = "blacklist:token:";
    private static final String USER_BLACKLIST_PREFIX = "blacklist:user:";

    private final StringRedisTemplate redisTemplate;

    public void blacklistToken(String token, Date expiration) {
        String key = BLACKLIST_PREFIX + token;
        long ttlMillis = expiration.getTime() - System.currentTimeMillis();

        if (ttlMillis > 0) {
            redisTemplate.opsForValue().set(key, "blacklisted", Duration.ofMillis(ttlMillis));
        }
    }

    public boolean isBlacklisted(String token) {
        String key = BLACKLIST_PREFIX + token;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * Blacklist all tokens for a user. Any token issued before this timestamp
     * will be rejected by the JwtAuthenticationFilter.
     * TTL matches the refresh token expiry (7 days) to cover all outstanding tokens.
     */
    public void blacklistUser(Long userId) {
        String key = USER_BLACKLIST_PREFIX + userId;
        String timestamp = String.valueOf(System.currentTimeMillis());
        redisTemplate.opsForValue().set(key, timestamp, Duration.ofDays(7));
    }

    /**
     * Check if a user was blacklisted after a given token issue time.
     */
    public boolean isUserBlacklisted(Long userId, long tokenIssuedAtMillis) {
        String key = USER_BLACKLIST_PREFIX + userId;
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return false;
        }
        long blacklistedAt = Long.parseLong(value);
        return tokenIssuedAtMillis <= blacklistedAt;
    }
}
