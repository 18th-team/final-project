package com.team.security;

import com.team.user.CustomOAuth2UserService;
import com.team.user.CustomUserDetailsService;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.header.writers.frameoptions.XFrameOptionsHeaderWriter;
import org.springframework.security.web.header.writers.frameoptions.XFrameOptionsHeaderWriter.XFrameOptionsMode;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;
    private final CustomUserDetailsService customUserDetailsService;

    public SecurityConfig(CustomOAuth2UserService customOAuth2UserService, CustomUserDetailsService customUserDetailsService) {
        this.customOAuth2UserService = customOAuth2UserService;
        this.customUserDetailsService = customUserDetailsService;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(authorizeHttpRequests ->
                        authorizeHttpRequests
                                .requestMatchers(
                                        new AntPathRequestMatcher("/"), // 메인 페이지는 비회원도 접근 가능
                                        new AntPathRequestMatcher("/clubs"), // 크루 리스트 페이지는 비회원 접근 가능
                                        new AntPathRequestMatcher("/error"),
                                        new AntPathRequestMatcher("/login"),
                                        new AntPathRequestMatcher("/signup"),
                                        new AntPathRequestMatcher("/mobti"),
                                        new AntPathRequestMatcher("/community"),
                                        new AntPathRequestMatcher("/clubs/category/**"),
                                        new AntPathRequestMatcher("/login/oauth2/**"),
                                        new AntPathRequestMatcher("/h2-console/**"),
                                        new AntPathRequestMatcher("/check-otp"),
                                        new AntPathRequestMatcher("/send-otp"),
                                        new AntPathRequestMatcher("/get-captcha"),
                                        new AntPathRequestMatcher("/css/**"), // CSS 허용
                                        new AntPathRequestMatcher("/js/**"),  // JS 허용 (필요 시)
                                        new AntPathRequestMatcher("/img/**"),  // 이미지 허용 (필요 시)
                                        new AntPathRequestMatcher("/upload/**"),  // 이미지 허용 (필요 시)
                                        new AntPathRequestMatcher("/font/**"), // 폰트 허용
                                        new AntPathRequestMatcher("/randomList"), // 랜덤리스트
                                        new AntPathRequestMatcher("/nearby"), // 랜덤리스트
                                        new AntPathRequestMatcher("/favicon.ico") // favicon 허용
                                ).permitAll() // 위 경로들은 모두 공개
                                .requestMatchers(new AntPathRequestMatcher("/api/check-auth")).authenticated()
                                .anyRequest().authenticated() // 나머지 경로는 인증 필요
                )
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers(
                                new AntPathRequestMatcher("/h2-console/**"),
                                new AntPathRequestMatcher("/chat/**"),
                                new AntPathRequestMatcher("/mypage/update"),
                                new AntPathRequestMatcher("/")
                        )
                )
                .headers(headers -> headers
                        .addHeaderWriter(new XFrameOptionsHeaderWriter(XFrameOptionsHeaderWriter.XFrameOptionsMode.SAMEORIGIN))
                )
                .formLogin(formLogin -> formLogin
                        .loginPage("/login")
                        .defaultSuccessUrl("/")
                        .usernameParameter("email")
                        .failureUrl("/login?error")
                        .successHandler((request, response, authentication) -> {
                            System.out.println("Form Login success: " + authentication.getName());
                            SecurityContextHolder.getContext().setAuthentication(authentication);
                            HttpSession session = request.getSession(true);
                            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                                    SecurityContextHolder.getContext());
                            System.out.println("Session ID after Form login: " + session.getId());
                            response.sendRedirect("/");
                        })
                        .permitAll()
                )
                .oauth2Login(oauth2Login -> oauth2Login
                        .loginPage("/login")
                        .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
                        .defaultSuccessUrl("/")
                        .failureUrl("/login?error")
                        .successHandler((request, response, authentication) -> {
                            System.out.println("OAuth2 Login success: " + authentication.getName());
                            SecurityContextHolder.getContext().setAuthentication(authentication);
                            HttpSession session = request.getSession(true);
                            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                                    SecurityContextHolder.getContext());
                            System.out.println("Session ID after OAuth2 login: " + session.getId());
                            response.sendRedirect("/");
                        })
                )
                .logout(logout -> logout
                        .logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
                        .logoutSuccessUrl("/")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                )
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) -> {
                            System.out.println("Authentication required for: " + request.getRequestURI());
                            response.sendRedirect("/login");
                        })
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .maximumSessions(1)
                        .expiredUrl("/login?expired")
                )
                .userDetailsService(customUserDetailsService);

        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }
}