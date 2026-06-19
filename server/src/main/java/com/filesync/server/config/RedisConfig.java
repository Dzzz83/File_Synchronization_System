package com.filesync.server.config;

import com.filesync.server.service.ChatEventRedisListener;
import com.filesync.server.service.FileEventRedisListener;
import com.filesync.server.service.FolderEventRedisListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class RedisConfig {

    @Bean
    public RedisMessageListenerContainer redisContainer(RedisConnectionFactory connectionFactory,
                                                        FileEventRedisListener fileListener,
                                                        ChatEventRedisListener chatListener,
                                                        FolderEventRedisListener folderListener) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(fileListener, new ChannelTopic("file-events"));
        container.addMessageListener(chatListener, new ChannelTopic("chat-events"));
        container.addMessageListener(folderListener, new ChannelTopic("folder-events"));
        return container;
    }
}