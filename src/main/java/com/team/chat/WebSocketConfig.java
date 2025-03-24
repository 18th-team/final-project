package com.team.chat;

import com.team.security.SecurityHandshakeInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.security.Principal;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/chat")
                .addInterceptors(new SecurityHandshakeInterceptor()) // 인터셉터 등록
                .setAllowedOrigins("http://localhost:8080")
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new org.springframework.messaging.support.ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, org.springframework.messaging.MessageChannel channel) {
                StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
                StompCommand command = accessor.getCommand();

                // 디버깅 로그 추가
                System.out.println("STOMP Command: " + command);

                // DISCONNECT는 인증 없이 통과
                if (command != null && command.equals(StompCommand.DISCONNECT)) {
                    return message;
                }

                // SecurityContextHolder에서 인증 정보 가져오기
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                System.out.println("Authentication from SecurityContext: " + (auth != null ? auth.getName() : "null"));

                // HandshakeInterceptor에서 설정된 Principal 확인
                Principal principal = accessor.getUser();
                System.out.println("Principal from accessor: " + (principal != null ? principal.getName() : "null"));

                // Principal이 있으면 이를 우선 사용, 없으면 SecurityContextHolder 사용
                if (principal == null) {
                    if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
                        throw new org.springframework.security.access.AccessDeniedException("Authentication required");
                    }
                    // Principal 설정 (람다 표현식 사용)
                    accessor.setUser(() -> auth.getName());
                } else {
                    // Principal이 이미 설정된 경우, 추가 설정 불필요
                    System.out.println("Using existing Principal: " + principal.getName());
                }

                return message;
            }
        });
    }
}