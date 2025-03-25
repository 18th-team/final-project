package com.team.chat;

import com.team.security.SecurityHandshakeInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/user");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/chat")
                .addInterceptors(new SecurityHandshakeInterceptor())
/*                .addInterceptors(new HttpSessionHandshakeInterceptor())*/
                .setAllowedOrigins("http://localhost:8080")
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
                System.out.println("STOMP Command: " + accessor.getCommand());
                System.out.println("Session ID from accessor: " + accessor.getSessionId());

                if (StompCommand.DISCONNECT.equals(accessor.getCommand())) {
                    System.out.println("Disconnect request, skipping authentication");
                    return message;
                }

                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                System.out.println("Authentication from SecurityContextHolder: " +
                        (auth != null ? auth.getName() : "null"));
                System.out.println("IsAuthenticated: " +
                        (auth != null && auth.isAuthenticated()));

                if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
                    accessor.setUser(auth);
                    System.out.println("Principal set: " + auth.getName());
                } else {
                    System.err.println("No valid authentication found for session: " + accessor.getSessionId());
                    throw new SecurityException("Authentication required");
                }
                return message;
            }
        });
    }
}