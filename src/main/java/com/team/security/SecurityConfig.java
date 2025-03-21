package com.team.security;

import com.team.user.CustomOAuth2UserService;
import com.team.user.CustomUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
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
                                        new AntPathRequestMatcher("/"),
                                        new AntPathRequestMatcher("/error"),
                                        new AntPathRequestMatcher("/login"),
                                        new AntPathRequestMatcher("/signup"),
                                        new AntPathRequestMatcher("/login/oauth2/**"),
                                        new AntPathRequestMatcher("/h2-console/**"),
                                        new AntPathRequestMatcher("/check-otp"),
                                        new AntPathRequestMatcher("/send-otp"),
                                        new AntPathRequestMatcher("/get-captcha"),
                                        new AntPathRequestMatcher("/css/**"), // CSS 허용
                                        new AntPathRequestMatcher("/js/**"),  // JS 허용 (필요 시)
                                        new AntPathRequestMatcher("/img/**"),  // 이미지 허용 (필요 시)
                                        new AntPathRequestMatcher("/font/**"), // 폰트 허용
                                        new AntPathRequestMatcher("/favicon.ico") // favicon 허용
                                ).permitAll() // 공개 경로
                                .anyRequest().authenticated() // 나머지 경로는 인증 필요
                )
                .csrf(csrf ->
                        csrf.ignoringRequestMatchers(new AntPathRequestMatcher("/h2-console/**")) // H2 콘솔 CSRF 제외
                )
                .headers(headers ->
                        headers.addHeaderWriter(new XFrameOptionsHeaderWriter(XFrameOptionsMode.SAMEORIGIN))
                )
                .formLogin(formLogin ->
                        formLogin
                                .loginPage("/login")
                                .defaultSuccessUrl("/")
                                .usernameParameter("email") // 로그인 시 email 사용
                                .failureUrl("/login?error")
                )
                .oauth2Login(oauth2Login ->
                        oauth2Login
                                .loginPage("/login")
                                .userInfoEndpoint(userInfo ->
                                        userInfo.userService(customOAuth2UserService) // OAuth2 사용자 서비스
                                )
                                .defaultSuccessUrl("/")
                                .failureUrl("/login?error")

                )
                .logout(logout ->
                        logout
                                .logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
                                .logoutSuccessUrl("/")
                                .invalidateHttpSession(true)
                                .deleteCookies("JSESSIONID")
                )
                .exceptionHandling(exception ->
                        exception.authenticationEntryPoint((request, response, authException) -> {
                            System.out.println("Authentication required for: " + request.getRequestURI());
                            response.sendRedirect("/login");
                        })
                )
                .userDetailsService(customUserDetailsService); // 폼 로그인용 UserDetailsService

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