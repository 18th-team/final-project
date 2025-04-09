package com.team.chat;

import com.team.security.SecurityHandshakeInterceptor;
import com.team.user.CustomSecurityUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
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
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${allowed.origins:http://localhost:8080}")
    private String[] allowedOrigins;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/user")
                .setHeartbeatValue(new long[] { 5000, 5000 })
                .setTaskScheduler(taskScheduler());
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/chat")
                .addInterceptors(new SecurityHandshakeInterceptor())
                .setAllowedOrigins(allowedOrigins)
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.taskExecutor()
                .corePoolSize(20)
                .maxPoolSize(50)
                .queueCapacity(1000);

        registration.interceptors(new ChannelInterceptor() {
            private final Map<String, Long> lastConnectTime = new ConcurrentHashMap<>();
            private final long rateLimitMillis = 2000;

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

                if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
                    System.err.println("Authentication failed for session: " + accessor.getSessionId());
                    throw new org.springframework.security.access.AccessDeniedException("Authentication required");
                }

                if (!(auth.getPrincipal() instanceof CustomSecurityUserDetails)) {
                    System.err.println("Invalid user details for session: " + accessor.getSessionId());
                    throw new org.springframework.security.access.AccessDeniedException("Invalid user details");
                }

                CustomSecurityUserDetails userDetails = (CustomSecurityUserDetails) auth.getPrincipal();
                String userUuid = userDetails.getSiteUser().getUuid();

                if (command != null && command.equals(StompCommand.CONNECT)) {
                    long now = System.currentTimeMillis();
                    Long lastTime = lastConnectTime.get(userUuid);
                    if (lastTime != null && (now - lastTime) < rateLimitMillis) {
                        System.err.println("Rate limit exceeded for user: " + userUuid);
                        throw new IllegalStateException("연결 속도 제한 초과");
                    }
                    lastConnectTime.put(userUuid, now);
                    accessor.getSessionAttributes().put("userUuid", userUuid);
                    System.out.println("Stored UUID in session: " + userUuid);

                    lastConnectTime.entrySet().removeIf(entry -> (now - entry.getValue()) > 60000);
                }

                accessor.setUser(new java.security.Principal() {
                    @Override
                    public String getName() {
                        return userUuid;
                    }
                });

                return message;
            }

            @Override
            public void afterSendCompletion(Message<?> message, MessageChannel channel, boolean sent, Exception ex) {
                StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
                if (ex != null) {
                    System.err.println("Error during STOMP message processing: " + ex.getMessage());
                    ex.printStackTrace();
                    if (accessor.getUser() != null) {
                        String destination = "/user/" + accessor.getUser().getName() + "/topic/errors";
                        StompHeaderAccessor errorAccessor = StompHeaderAccessor.create(StompCommand.ERROR);
                        errorAccessor.setMessage("서버 오류: " + ex.getMessage());
                        errorAccessor.setLeaveMutable(true); // 헤더 수정 가능하도록 설정

                        // MessageBuilder를 사용하여 destination을 포함한 메시지 생성
                        Message<String> errorMessage = MessageBuilder.createMessage(
                                "", // ERROR 프레임의 본문은 비어 있어도 됨
                                errorAccessor.getMessageHeaders()
                        );
                        errorAccessor.setDestination(destination); // destination 설정
                        channel.send(errorMessage);
                    }
                }
                if (accessor.getCommand() == StompCommand.DISCONNECT) {
                    System.out.println("Client disconnected: " + accessor.getSessionId());
                    String userUuid = (String) accessor.getSessionAttributes().get("userUuid");
                    if (userUuid != null) {
                        lastConnectTime.remove(userUuid);
                    }
                }
            }
        });
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.taskExecutor()
                .corePoolSize(20)
                .maxPoolSize(50)
                .queueCapacity(1000);
    }

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(20);
        scheduler.setThreadNamePrefix("wss-heartbeat-");
        scheduler.initialize();
        return scheduler;
    }
}