package com.chirpchat.chat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/** STOMP at /ws. CONNECT must carry a valid access token; clients may only subscribe to their own queues. */
@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtDecoder jwtDecoder;
    private final String[] allowedOrigins;

    WebSocketConfig(JwtDecoder jwtDecoder, @Value("${chat.websocket.allowed-origins:*}") String[] allowedOrigins) {
        this.jwtDecoder = jwtDecoder;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns(allowedOrigins);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
                StompHeaderAccessor stomp = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (stomp == null || stomp.getCommand() == null) {
                    return message;
                }
                if (stomp.getCommand() == StompCommand.CONNECT) {
                    String header = stomp.getFirstNativeHeader("Authorization");
                    if (header == null || !header.startsWith("Bearer ")) {
                        throw new MessagingException("A bearer token is required");
                    }
                    try {
                        stomp.setUser(CurrentUser.of(jwtDecoder.decode(header.substring(7))));
                    } catch (JwtException e) {
                        throw new MessagingException("Invalid token");
                    }
                } else if (stomp.getCommand() == StompCommand.SUBSCRIBE) {
                    String destination = stomp.getDestination();
                    if (stomp.getUser() == null || destination == null || !destination.startsWith("/user/queue/")) {
                        throw new MessagingException("Subscriptions are limited to /user/queue destinations");
                    }
                } else if (stomp.getCommand() == StompCommand.SEND) {
                    throw new MessagingException("Send messages over HTTP");
                }
                return message;
            }
        });
    }
}
