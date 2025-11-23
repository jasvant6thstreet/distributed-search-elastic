package com.search.security;

import com.google.common.util.concurrent.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Token bucket rate limiter per tenant
 */
@Slf4j
@Component
public class TenantRateLimiter {
    
    private final Map<String, RateLimiter> limiters = new ConcurrentHashMap<>();
    private final double permitsPerSecond;
    
    public TenantRateLimiter() {
        this.permitsPerSecond = 100.0; // 100 requests per second per tenant
    }
    
    public TenantRateLimiter(double permitsPerSecond) {
        this.permitsPerSecond = permitsPerSecond;
    }
    
    /**
     * Check if request is allowed for tenant
     */
    public boolean isAllowed(String tenantId) {
        RateLimiter limiter = limiters.computeIfAbsent(
            tenantId, 
            k -> RateLimiter.create(permitsPerSecond)
        );
        
        boolean allowed = limiter.tryAcquire();
        
        if (!allowed) {
            log.warn("Rate limit exceeded for tenant: {}", tenantId);
        }
        
        return allowed;
    }
    
    /**
     * Get rate limiter for a tenant
     */
    public RateLimiter getRateLimiter(String tenantId) {
        return limiters.computeIfAbsent(
            tenantId,
            k -> RateLimiter.create(permitsPerSecond)
        );
    }
}
