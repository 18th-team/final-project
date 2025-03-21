package com.team.user;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    @Autowired
    private UserRepository userRepository; // SiteUserRepository → UserRepository

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        System.out.println("OAuth2UserRequest: " + userRequest);
        OAuth2User oAuth2User = super.loadUser(userRequest);
        System.out.println("OAuth2User Attributes: " + oAuth2User.getAttributes());
        // OAuth 제공자 정보
        String provider = userRequest.getClientRegistration().getRegistrationId(); // "kakao" 또는 "naver"
        String providerId = getProviderId(oAuth2User, provider);
        String email = getEmail(oAuth2User, provider);
        String name = getName(oAuth2User, provider);
        String gender = getGender(oAuth2User, provider);
        String Phone = getPhoneNumber(oAuth2User, provider).replace("-","");
        LocalDate birthDate = getBirthDate(oAuth2User, provider);
        System.out.println("Provider: " + provider + ", ProviderId: " + providerId + ", Email: " + email + ", BirthDate: " + getAge(birthDate) + ", Gender: " + gender + ", Phone: " + Phone);
        // 이메일이 중복될 경우 providerId를 붙여 고유성 보장
        String uniqueEmail = email != null ? email : provider + "_" + providerId;

        // 데이터베이스에서 사용자 조회 또는 생성
        SiteUser siteUser = userRepository.findByProviderAndProviderId(provider, providerId)
                .orElseGet(() -> {
                        SiteUser newUser = SiteUser.builder()
                                .email(uniqueEmail)
                                .name(name != null ? name : "소셜 사용자")
                                .provider(provider)
                                .providerId(providerId)
                                .role(MemberRole.USER)
                                .age(getAge(birthDate))
                                .gender(gender)
                                .phone(Phone)
                                .money(0)
                                .createdAt(LocalDate.now())
                                .build();
                    return userRepository.save(newUser);
                });

        return new CustomSecurityUserDetails(siteUser, oAuth2User.getAttributes());
    }

    private String getProviderId(OAuth2User oAuth2User, String provider) {
        if ("kakao".equals(provider)) {
            return oAuth2User.getAttribute("id").toString();
        } else if ("naver".equals(provider)) {
            Map<String, Object> response = oAuth2User.getAttribute("response");
            return response != null ? response.get("id").toString() : null;
        }
        throw new IllegalArgumentException("지원하지 않는 제공자: " + provider);
    }

    private String getName(OAuth2User oAuth2User, String provider) {
        if ("kakao".equals(provider)) {
            Map<String, Object> properties = oAuth2User.getAttribute("properties");
            return properties != null ? (String) properties.get("nickname") : null;
        } else if ("naver".equals(provider)) {
            Map<String, Object> response = oAuth2User.getAttribute("response");
            return response != null ? (String) response.get("name") : null;
        }
        return null;
    }

    private String getGender(OAuth2User oAuth2User, String provider) {
        if ("kakao".equals(provider)) {
            Map<String, Object> kakaoAccount = oAuth2User.getAttribute("kakao_account");
            return kakaoAccount != null ? (String) kakaoAccount.get("gender") : null;
        } else if ("naver".equals(provider)) {
            Map<String, Object> response = oAuth2User.getAttribute("response");
            return response != null ? (String) response.get("gender") : null;
        }
        return null;
    }

    private String getPhoneNumber(OAuth2User oAuth2User, String provider) {
        if ("kakao".equals(provider)) {
            Map<String, Object> kakaoAccount = oAuth2User.getAttribute("kakao_account");
            return kakaoAccount != null ? (String) kakaoAccount.get("phone_number") : null;
        } else if ("naver".equals(provider)) {
            Map<String, Object> response = oAuth2User.getAttribute("response");
            return response != null ? (String) response.get("mobile") : null;
        }
        return null;
    }


    private String getEmail(OAuth2User oAuth2User, String provider) {
        if ("kakao".equals(provider)) {
            Map<String, Object> kakaoAccount = oAuth2User.getAttribute("kakao_account");
            return kakaoAccount != null ? (String) kakaoAccount.get("email") : null;
        } else if ("naver".equals(provider)) {
            Map<String, Object> response = oAuth2User.getAttribute("response");
            return response != null ? (String) response.get("email") : null;
        }
        return null;
    }
    private int getAge(LocalDate birthDate) {
        if (birthDate == null) return 0;
        LocalDate today = LocalDate.now();
        int age = today.getYear() - birthDate.getYear();
        return age;
    }
    private LocalDate getBirthDate(OAuth2User oAuth2User, String provider) {
        try {
            if ("kakao".equals(provider)) {
                Map<String, Object> kakaoAccount = oAuth2User.getAttribute("kakao_account");
                String birthday = kakaoAccount != null ? (String) kakaoAccount.get("birthday") : null;
                String birthyear = kakaoAccount != null ? (String) kakaoAccount.get("birthyear") : null;
                if (birthday != null && birthyear != null) {
                    String fullDate = birthyear + birthday;
                    return LocalDate.parse(fullDate, DateTimeFormatter.BASIC_ISO_DATE);
                }
            } else if ("naver".equals(provider)) {
                Map<String, Object> response = oAuth2User.getAttribute("response");
                String birthday = response != null ? (String) response.get("birthday") : null;
                String birthyear = response != null ? (String) response.get("birthyear") : null;
                if (birthday != null && birthyear != null) {
                    String fullDate = birthyear + birthday.replace("-", "");
                    return LocalDate.parse(fullDate, DateTimeFormatter.BASIC_ISO_DATE);
                }
            }
        } catch (Exception e) {
            System.out.println("BirthDate 파싱 실패: " + e.getMessage());
        }
        return null;
    }
}