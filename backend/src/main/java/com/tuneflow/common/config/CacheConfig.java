package com.tuneflow.common.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CacheManager selection (docs section 22 - Redis strategy).
 *
 * <p>Only high-read / low-volatility data is cached: home feed fragments, trending songs, popular
 * artists, song metadata, recommendation results and search results. Mutable transactional data
 * (playlists, history, likes) is deliberately not cached; those paths use explicit
 * {@code @CacheEvict} calls when they do touch cached catalog entries.
 *
 * <p>{@code tuneflow.cache.provider=caffeine} (default) keeps local development dependency-free.
 * {@code tuneflow.cache.provider=redis} switches to a shared Redis cache with per-cache TTLs.
 */
@Slf4j
@Configuration
public class CacheConfig {

    public static final String CACHE_SONGS = "songs";
    public static final String CACHE_ALBUMS = "albums";
    public static final String CACHE_ARTISTS = "artists";
    public static final String CACHE_GENRES = "genres";
    public static final String CACHE_SEARCH = "search";
    public static final String CACHE_HOME_FEED = "homeFeed";
    public static final String CACHE_RECOMMENDATIONS = "recommendations";
    public static final String CACHE_TRENDING = "trending";

    public static final List<String> ALL_CACHES = List.of(
            CACHE_SONGS, CACHE_ALBUMS, CACHE_ARTISTS, CACHE_GENRES,
            CACHE_SEARCH, CACHE_HOME_FEED, CACHE_RECOMMENDATIONS, CACHE_TRENDING);

    @Bean
    @ConditionalOnProperty(name = "tuneflow.cache.provider", havingValue = "caffeine", matchIfMissing = true)
    public CacheManager caffeineCacheManager(AppProperties properties) {
        log.info("Using Caffeine (in-process) cache provider");
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCacheNames(ALL_CACHES);
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(properties.cache().defaultTtl())
                // Required for cache hit/miss metrics on the Prometheus endpoint (docs section 35).
                .recordStats());
        return manager;
    }

    @Bean
    @ConditionalOnProperty(name = "tuneflow.cache.provider", havingValue = "redis")
    public CacheManager redisCacheManager(RedisConnectionFactory connectionFactory, AppProperties properties) {
        log.info("Using Redis cache provider at {}", connectionFactory.getConnection().getNativeConnection());
        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(properties.cache().defaultTtl())
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()));

        Map<String, RedisCacheConfiguration> perCache = new HashMap<>();
        perCache.put(CACHE_SONGS, base.entryTtl(properties.cache().songTtl()));
        perCache.put(CACHE_ALBUMS, base.entryTtl(properties.cache().albumTtl()));
        perCache.put(CACHE_ARTISTS, base.entryTtl(properties.cache().artistTtl()));
        perCache.put(CACHE_GENRES, base.entryTtl(properties.cache().artistTtl()));
        perCache.put(CACHE_SEARCH, base.entryTtl(properties.cache().searchTtl()));
        perCache.put(CACHE_HOME_FEED, base.entryTtl(properties.cache().homeFeedTtl()));
        perCache.put(CACHE_TRENDING, base.entryTtl(properties.cache().homeFeedTtl()));
        perCache.put(CACHE_RECOMMENDATIONS, base.entryTtl(properties.cache().recommendationTtl()));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(base)
                .withInitialCacheConfigurations(perCache)
                .transactionAware()
                .build();
    }
}
