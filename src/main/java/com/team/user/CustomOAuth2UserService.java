package com.team.user;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    @Autowired
    private UserRepository userRepository; // SiteUserRepository → UserRepository

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        // OAuth 제공자 정보
        String provider = userRequest.getClientRegistration().getRegistrationId(); // "kakao" 또는 "naver"
        String providerId = getProviderId(oAuth2User, provider);
        String email = getEmail(oAuth2User, provider);
        String name = getName(oAuth2User, provider);

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
                                .age(0)
                                .gender("unknown")
                                .money(0)
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

    private Integer getAge(OAuth2User oAuth2User, String provider) {
        if ("kakao".equals(provider)) {
            Map<String, Object> kakaoAccount = oAuth2User.getAttribute("kakao_account");
            String ageRange = kakaoAccount != null ? (String) kakaoAccount.get("age_range") : null;
            return ageRange != null ? parseAgeRange(ageRange) : null;
        } else if ("naver".equals(provider)) {
            Map<String, Object> response = oAuth2User.getAttribute("response");
            String ageRange = response != null ? (String) response.get("age") : null;
            return ageRange != null ? parseAgeRange(ageRange) : null;
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

    private Integer parseAgeRange(String ageRange) {
        if (ageRange == null) return null;
        String[] range = ageRange.split("~");
        if (range.length == 2) {
            int min = Integer.parseInt(range[0]);
            int max = Integer.parseInt(range[1]);
            return (min + max) / 2;
        } else if (range.length == 1) {
            return Integer.parseInt(range[0]);
        }
        return null;
    }
}