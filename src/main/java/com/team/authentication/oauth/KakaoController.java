package com.team.authentication.oauth;

import com.team.user.CustomSecurityUserDetails;
import com.team.user.SiteUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigInteger;
import java.security.SecureRandom;

@Controller
@RequestMapping("/login/oauth2")
public class KakaoController {

    @Value("${spring.security.oauth2.client.provider.kakao.authorization-uri}")
    private String authorizeUri;

    @Value("${spring.security.oauth2.client.registration.kakao.client-id}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.kakao.redirect-uri}")
    private String redirectUri;

    // 카카오 로그인 URL 생성 및 리다이렉트
    @GetMapping("/kakao")
    public String kakao() {
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

        System.out.println("Kakao Redirect URL: " + redirectUrl);
        return "redirect:" + redirectUrl;
    }

    // 카카오 로그인 성공 처리
    @GetMapping("/code/kakao")
    public String kakaoLoginSuccess(@AuthenticationPrincipal CustomSecurityUserDetails userDetails, Model model) {
        if (userDetails != null) {
            SiteUser siteUser = userDetails.getSiteUser();
            model.addAttribute("name", siteUser.getName());
            model.addAttribute("gender", siteUser.getGender());
            model.addAttribute("phone", siteUser.getPhone());
            model.addAttribute("age", siteUser.getAge());
            model.addAttribute("provider", siteUser.getProvider());
            return "kakao-success";
        }
        return "redirect:/login?error";
    }
}