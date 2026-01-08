package SEP490.EduPrompt.config;

import SEP490.EduPrompt.service.ai.QueueEventListener;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
@EnableCaching
@Slf4j
public class RedisConfig {

    public static final String OPTIMIZATION_QUEUE_TOPIC = "queue:optimization";
    public static final String TEST_QUEUE_TOPIC = "queue:test";
    public static final String UPLOAD_TOPIC = "file:upload";


    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(stringSerializer);

        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(24))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .build();
    }

    /**
     * Container for Redis message listeners
     */
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            MessageListenerAdapter optimizationListenerAdapter,
            MessageListenerAdapter testListenerAdapter,
            MessageListenerAdapter uploadListenerAdapter) {

        log.info("Initializing Redis message listener container");

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);

        // Register optimization queue listener
        container.addMessageListener(
                optimizationListenerAdapter,
                new ChannelTopic(OPTIMIZATION_QUEUE_TOPIC)
        );

        // Register test queue listener
        container.addMessageListener(
                testListenerAdapter,
                new ChannelTopic(TEST_QUEUE_TOPIC)
        );

        container.addMessageListener(
                uploadListenerAdapter,
                new ChannelTopic(UPLOAD_TOPIC)
        );

        log.info("Redis listeners registered for topics: {}, {}, {}",
                OPTIMIZATION_QUEUE_TOPIC, TEST_QUEUE_TOPIC, UPLOAD_TOPIC);

        return container;
    }

    /**
     * Adapter for optimization queue messages
     */
    @Bean
    public MessageListenerAdapter optimizationListenerAdapter(QueueEventListener listener) {
        return new MessageListenerAdapter(listener, "onOptimizationQueued");
    }

    /**
     * Adapter for test queue messages
     */
    @Bean
    public MessageListenerAdapter testListenerAdapter(QueueEventListener listener) {
        return new MessageListenerAdapter(listener, "onTestQueued");
    }

    /**
     * Adapter for file upload messages
     */
    @Bean
    public MessageListenerAdapter uploadListenerAdapter(QueueEventListener listener) {
        return new MessageListenerAdapter(listener, "onFileUploadRequested");
    }
}

