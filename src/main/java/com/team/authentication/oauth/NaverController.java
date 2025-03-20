package com.team.authentication.oauth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.math.BigInteger;
import java.security.SecureRandom;

@Controller
public class NaverController {
    private final WebClient webClient = WebClient.create();
    @Value("${naver.authorize-uri}")
    private String authorizeUri;
    
    @Value("${naver.authorize-uri}")
    private String requestTokenUri;

    @Value("${naver.client-id}")
    private String clientId;

    @Value("${naver.client-secret}")
    private String clientSecret;

    @Value("${naver.redirect-uri}")
    private String redirectUri;

    @GetMapping("/naver")
    public String naver() {
        // SecureRandom으로 state 값 생성
        SecureRandom random = new SecureRandom();
        String state = new BigInteger(130, random).toString();

        // UriComponentsBuilder로 네이버 인가 코드 요청 URL 생성
        String redirectUrl = UriComponentsBuilder.fromUriString(authorizeUri)
                .queryParam("response_type", "code")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("state", state)
                .build()
                .encode() // URL 인코딩 처리
                .toUriString();


        // 생성된 URL로 리다이렉트
        return "redirect:" + redirectUrl;
    }
    @GetMapping("/naver/callback")
    public Mono<String> naverCallback(@RequestParam("code") String code, @RequestParam("state") String state, Model model) {
        // UriComponentsBuilder로 네이버 인가 코드 요청 URL 생성
        String tokenUrl = UriComponentsBuilder.fromUriString(requestTokenUri)
                .queryParam("grant_type", "authorization_code")
                .queryParam("client_id", clientId)
                .queryParam("client_secret", clientSecret)
                .queryParam("code", code)
                .queryParam("state", state)
                .build()
                .encode() // URL 인코딩 처리
                .toUriString();


        return webClient.get()
                .uri(tokenUrl)
                .retrieve()
                .bodyToMono(String.class)
                .doOnNext(response -> {
                    System.out.println("응답: " + response);
                    model.addAttribute("response", response); // 모델에 응답 추가
                })
                .thenReturn("callback"); // 뷰 이름 반환
    }
}