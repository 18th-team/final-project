package com.team.chat;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {
    @GetMapping("/api/check-auth")
    public ResponseEntity<String> checkAuth(Authentication auth, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        System.out.println("Check-auth - Session ID: " + (session != null ? session.getId() : "null"));
        System.out.println("Check-auth - Authentication: " + (auth != null ? auth.getName() : "null"));
        System.out.println("Check-auth - IsAuthenticated: " + (auth != null && auth.isAuthenticated()));
        if (session != null) {
            SecurityContext context = (SecurityContext) session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
            System.out.println("Check-auth - SecurityContext from session: " +
                    (context != null && context.getAuthentication() != null ? context.getAuthentication().getName() : "null"));
        }

        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
            return ResponseEntity.ok(auth.getName());
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Not authenticated");
        }
    }
}
