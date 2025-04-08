package com.team.security;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import jakarta.servlet.http.HttpSession;
import java.util.Map;

public class SecurityHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        if (request instanceof ServletServerHttpRequest) {
            HttpSession session = ((ServletServerHttpRequest) request).getServletRequest().getSession(false);
            if (session == null) {
                System.out.println("No session found, denying handshake");
                return false;
            }

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();

            // 👇 세션에서 인증 정보 강제로 꺼내기
            if ((auth == null || !auth.isAuthenticated()) && session != null) {
                SecurityContext context = (SecurityContext) session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
                if (context != null) {
                    auth = context.getAuthentication();
                    SecurityContextHolder.setContext(context);
                }
            }

            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
                attributes.put("authentication", auth);
                System.out.println("Handshake set authentication for user: " + auth.getName());
                return true;
            } else {
                System.out.println("Authentication failed, denying handshake");
                return false;
            }
        }
        System.out.println("Invalid request type, denying handshake");
        return false;
    }


    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 필요 시 후처리 로직 추가
    }
}