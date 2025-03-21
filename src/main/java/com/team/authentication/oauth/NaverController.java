package com.team.authentication.oauth;

import com.team.user.CustomSecurityUserDetails;
import com.team.user.SiteUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.math.BigInteger;
import java.security.SecureRandom;

@Controller
@RequestMapping("/login/oauth2")
public class NaverController {

    @Value("${spring.security.oauth2.client.provider.naver.authorization-uri}")
    private String authorizeUri;

    @Value("${spring.security.oauth2.client.registration.naver.client-id}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.naver.redirect-uri}")
    private String redirectUri;

    // 네이버 로그인 URL 생성 및 리다이렉트
    @GetMapping("/naver")
    public String naver() {
        SecureRandom random = new SecureRandom();
        String state = new BigInteger(130, random).toString();

        String redirectUrl = UriComponentsBuilder.fromUriString(authorizeUri)
                .queryParam("response_type", "code")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("state", state)
                .build()
                .encode()
                .toUriString();

        System.out.println("Naver Redirect URL: " + redirectUrl);
        return "redirect:" + redirectUrl;
    }

    // 네이버 로그인 성공 처리
    @GetMapping("/code/naver")
    public String naverLoginSuccess(@AuthenticationPrincipal CustomSecurityUserDetails userDetails, Model model) {
        if (userDetails != null) {
            SiteUser siteUser = userDetails.getSiteUser();
            model.addAttribute("name", siteUser.getName());
            model.addAttribute("gender", siteUser.getGender());
            model.addAttribute("phone", siteUser.getPhone());
            model.addAttribute("age", siteUser.getAge());
            model.addAttribute("provider", siteUser.getProvider());
            return "naver-success";
        }
        System.out.println("네이버 로그인 실패: 사용자 정보가 null입니다.");
        return "redirect:/login?error";
    }
}