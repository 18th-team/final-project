package com.team.chat;

import com.team.security.SecurityHandshakeInterceptor;
import com.team.user.CustomSecurityUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.security.core.Authentication;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/user")
                .setHeartbeatValue(new long[] { 5000, 5000 }).setTaskScheduler(taskScheduler());// 서버 하트비트 간격 (ms);
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
            private final Map<String, Long> lastConnectTime = new ConcurrentHashMap<>();
            private final long rateLimitMillis = 2000; // 1초당 1 연결

            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
                StompCommand command = accessor.getCommand();
                System.out.println("Inbound STOMP Command: " + command);

                Authentication auth = (Authentication) accessor.getSessionAttributes().get("authentication");
                if (command != null && command.equals(StompCommand.DISCONNECT)) {
                    System.out.println("Client disconnected: " + accessor.getSessionId());
                    return message;
                }
                if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
                    if (auth.getPrincipal() instanceof CustomSecurityUserDetails) {
                        CustomSecurityUserDetails userDetails = (CustomSecurityUserDetails) auth.getPrincipal();
                        String userUuid = userDetails.getSiteUser().getUuid();

                        if (command != null && command.equals(StompCommand.CONNECT)) {
                            long now = System.currentTimeMillis();
                            Long lastTime = lastConnectTime.get(userUuid);
                            if (lastTime != null && (now - lastTime) < rateLimitMillis) {
                                throw new IllegalStateException("연결 속도 제한 초과");
                            }
                            lastConnectTime.put(userUuid, now);
                            accessor.getSessionAttributes().put("userUuid", userUuid);
                            System.out.println("Stored UUID in session: " + userUuid);
                        }

                        accessor.setUser(new java.security.Principal() {
                            @Override
                            public String getName() {
                                return userUuid;
                            }
                        });
                    } else {
                        throw new org.springframework.security.access.AccessDeniedException("Invalid user details");
                    }
                } else {
                    throw new org.springframework.security.access.AccessDeniedException("Authentication required");
                }

                return message;
            }
            @Override
            public void afterSendCompletion(Message<?> message, MessageChannel channel, boolean sent, Exception ex) {
                StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
                if (ex != null) {
                    System.err.println("Error during STOMP message processing: " + ex.getMessage());
                }
                if (accessor.getCommand() == StompCommand.DISCONNECT) {
                    System.out.println("Client disconnected: " + accessor.getSessionId());
                }
            }
        });
    }
    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(5); // 스레드 풀 크기 설정
        scheduler.setThreadNamePrefix("wss-heartbeat-thread-"); // 스레드 이름 접두사
        scheduler.initialize();
        return scheduler;
    }

}