package com.team.security;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
                return false; // 세션 없으면 차단
            }
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
                attributes.put("authentication", auth);
                System.out.println("Handshake set authentication for user: " + auth.getName());
                return true;
            } else {
                System.out.println("Authentication failed, denying handshake");
                return false; // 인증 없으면 차단
            }
        }
        System.out.println("Invalid request type, denying handshake");
        return false; // ServletServerHttpRequest가 아니면 차단
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 필요 시 후처리 로직 추가
    }
}