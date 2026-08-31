package com.olehprukhnytskyi.macrotrackerfoodservice.config;

import com.olehprukhnytskyi.macrotrackerfoodservice.util.CacheConstants;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer();
        template.setDefaultSerializer(serializer);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.setHashValueSerializer(serializer);

        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory cf) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues();

        Map<String, RedisCacheConfiguration> configs = new HashMap<>();
        configs.put(CacheConstants.FOOD_DATA, defaultConfig.entryTtl(Duration.ofHours(24)));
        configs.put(CacheConstants.SEARCH_SUGGESTIONS, defaultConfig
                .entryTtl(Duration.ofMinutes(30)));
        configs.put(CacheConstants.SEARCH_RESULTS, defaultConfig.entryTtl(Duration.ofMinutes(30)));
        configs.put(CacheConstants.FOOD_PHOTO_HISTORY,
                defaultConfig.entryTtl(Duration.ofMinutes(2)));
        configs.put(CacheConstants.FOOD_PHOTO_ENTITLEMENT,
                defaultConfig.entryTtl(Duration.ofSeconds(15)));

        return RedisCacheManager.builder(cf)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(configs)
                .build();
    }
}
