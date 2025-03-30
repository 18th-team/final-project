package com.team.chat;

import com.team.security.SecurityHandshakeInterceptor;
import com.team.user.CustomSecurityUserDetails;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.core.Authentication;
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
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
                StompCommand command = accessor.getCommand();
                System.out.println("Inbound STOMP Command: " + command);

                if (command != null && command.equals(StompCommand.DISCONNECT)) {
                    return message;
                }

                Authentication auth = (Authentication) accessor.getSessionAttributes().get("authentication");
                if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
                    if (auth.getPrincipal() instanceof CustomSecurityUserDetails) {
                        CustomSecurityUserDetails userDetails = (CustomSecurityUserDetails) auth.getPrincipal();
                        String userUuid = userDetails.getSiteUser().getUuid();
                        System.out.println("Set Principal with UUID from SiteUser: " + userUuid);

                        accessor.setUser(new java.security.Principal() {
                            @Override
                            public String getName() {
                                return userUuid;
                            }
                        });

                        if (command != null && command.equals(StompCommand.CONNECT)) {
                            accessor.getSessionAttributes().put("userUuid", userUuid);
                            System.out.println("Stored UUID in session: " + userUuid);
                        }
                    } else {
                        throw new org.springframework.security.access.AccessDeniedException("Invalid user details");
                    }
                } else {
                    throw new org.springframework.security.access.AccessDeniedException("Authentication required");
                }

                return message;
            }
        });
    }
}