package com.example.tripminglematching.config;


import com.example.tripminglematching.listener.RedisMessageSubscriber;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    private final String host;
    private final int port;

    public RedisConfig(@Value("${spring.data.redis.host}") String host, @Value("${spring.data.redis.port}") int port) {
        this.host = host;
        this.port = port;
    }

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        return new LettuceConnectionFactory(host, port);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate() {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(redisConnectionFactory());
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        return redisTemplate;
    }

    @Bean
    public RedisMessageListenerContainer redisContainer(RedisConnectionFactory connectionFactory,
                                                        MessageListenerAdapter messageListener) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);  // RedisConnectionFactory를 설정합니다.

        container.addMessageListener(messageListener, addUserTopic());
        container.addMessageListener(messageListener, deleteUserTopic());
        container.addMessageListener(messageListener, reCalculateUserTopic());
        container.addMessageListener(messageListener, matchingTopic());


        return container;
    }

    @Bean
    public MessageListenerAdapter messageListener(RedisMessageSubscriber redisMessageSubscriber) {
        return new MessageListenerAdapter(redisMessageSubscriber);  // RedisMessageSubscriber를 메시지 리스너로 설정합니다.
    }

    @Bean
    public ChannelTopic addUserTopic() {
        return new ChannelTopic("pubsub:addUser");
    }

    @Bean
    public ChannelTopic reCalculateUserTopic() {
        return new ChannelTopic("pubsub:reCalculateUser");
    }

    @Bean
    public ChannelTopic deleteUserTopic() {
        return new ChannelTopic("pubsub:deleteUser");
    }

    @Bean
    public ChannelTopic matchingTopic() {
        return new ChannelTopic("pubsub:matching");
    }



}

