package com.sakurain.gpuscheduler.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final String[] BROKER_DESTINATIONS = {"/topic", "/queue"};
    private static final String[] APPLICATION_DESTINATIONS = {"/app"};
    private static final String[] ALL_ORIGINS = {"*"};

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker(BROKER_DESTINATIONS);
        registry.setApplicationDestinationPrefixes(APPLICATION_DESTINATIONS);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(ALL_ORIGINS);

        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(ALL_ORIGINS)
                .withSockJS();

        registry.addEndpoint("/ws/public")
                .setAllowedOriginPatterns(ALL_ORIGINS)
                .withSockJS();
    }
}
