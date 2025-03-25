package com.team.chat;

import com.team.security.SecurityHandshakeInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

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
                .setAllowedOrigins("http://localhost:8080")
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new org.springframework.messaging.support.ChannelInterceptor() {
            @Override
            public org.springframework.messaging.Message<?> preSend(org.springframework.messaging.Message<?> message,
                                                                    org.springframework.messaging.MessageChannel channel) {
                org.springframework.messaging.simp.stomp.StompHeaderAccessor accessor = org.springframework.messaging.simp.stomp.StompHeaderAccessor.wrap(message);
                org.springframework.messaging.simp.stomp.StompCommand command = accessor.getCommand();
                System.out.println("STOMP Command: " + command);

                if (command != null && command.equals(org.springframework.messaging.simp.stomp.StompCommand.DISCONNECT)) {
                    return message;
                }

                org.springframework.security.core.Authentication auth = (org.springframework.security.core.Authentication) accessor.getSessionAttributes().get("authentication");
                if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
                    accessor.setUser(auth);
                    System.out.println("Set Principal from session attributes: " + auth.getName());
                } else {
                    System.out.println("No valid authentication found in session attributes");
                    throw new org.springframework.security.access.AccessDeniedException("Authentication required");
                }

                return message;
            }
        });
    }
}