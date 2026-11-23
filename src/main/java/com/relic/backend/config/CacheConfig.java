package com.relic.backend.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Cache names are fixed here rather than discovered: "projects", "tasks"
 * and "users", each keyed by the entity's id (see @Cacheable usages on the
 * corresponding *ServiceImpl.getById methods).
 *
 * Two cache manager implementations are wired, one per environment:
 *
 *  - dev / test: an in-process ConcurrentMapCacheManager. No external
 *    dependency, no serialization, good enough for a single instance and
 *    for integration tests that must not require a running Redis.
 *
 *  - postgres (the production-like profile): a real RedisCacheManager, so
 *    the cache is shared across multiple application instances and survives
 *    a single instance restarting. A 10 minute TTL is used as a blunt
 *    invalidation backstop; day-to-day invalidation is still expected to
 *    happen explicitly via @CacheEvict on the owning service's mutating
 *    methods (see the class javadoc on ProjectServiceImpl/TaskServiceImpl/
 *    UserServiceImpl), because a change made through a path that bypasses
 *    those methods - as ApprovalServiceImpl does when it updates a Task's
 *    status directly - will not trigger a Spring Cache eviction and must
 *    evict the relevant cache entry itself (see ApprovalServiceImpl).
 *    Null values are never cached, so a resource that gets created right
 *    after a failed lookup is not stuck behind a stale "not found".
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    @Profile({"dev", "test"})
    public CacheManager concurrentMapCacheManager() {
        ConcurrentMapCacheManager manager = new ConcurrentMapCacheManager("projects", "tasks", "users");
        manager.setAllowNullValues(false);
        return manager;
    }

    @Bean
    @Profile("postgres")
    public CacheManager redisCacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration baseConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(baseConfig)
                .withCacheConfiguration("projects", baseConfig)
                .withCacheConfiguration("tasks", baseConfig)
                .withCacheConfiguration("users", baseConfig)
                .build();
    }
}
