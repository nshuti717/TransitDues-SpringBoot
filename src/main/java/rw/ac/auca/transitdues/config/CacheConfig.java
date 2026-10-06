package rw.ac.auca.transitdues.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

/**
 * Redis-backed cache for the dashboard numbers only. Entities are never cached
 * here; the dashboardStats cache holds nothing but the DashboardStats record.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    private static final String DASHBOARD_STATS_CACHE = "dashboardStats";

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {
        /*
         * Default typing (the "@class" hint) is required so a cache hit deserializes
         * back into the exact DashboardStats record instead of a generic Map. This is
         * safe here only because this serializer reads nothing but data this same app
         * wrote to its own Redis cache - it never deserializes untrusted input.
         */
        GenericJacksonJsonRedisSerializer jsonSerializer = GenericJacksonJsonRedisSerializer.builder()
                .enableUnsafeDefaultTyping()
                .build();

        RedisCacheConfiguration dashboardStatsConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(60))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer));

        return RedisCacheManager.builder(redisConnectionFactory)
                .withCacheConfiguration(DASHBOARD_STATS_CACHE, dashboardStatsConfig)
                .build();
    }
}
