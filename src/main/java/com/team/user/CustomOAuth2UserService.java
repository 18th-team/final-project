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
import java.util.Optional;
import java.util.UUID;

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


        // 1. provider와 providerId로 기존 OAuth 사용자 조회
        Optional<SiteUser> existingOAuthUser = userRepository.findByProviderAndProviderId(provider, providerId);
        if (existingOAuthUser.isPresent()) {
            SiteUser siteUser = existingOAuthUser.get();
            return new CustomSecurityUserDetails(siteUser, oAuth2User.getAttributes());
        }
        // 2. 이메일 중복 체크
        Optional<SiteUser> existingUserByEmail = userRepository.findByEmail(uniqueEmail);
        if (existingUserByEmail.isPresent()) {
            SiteUser user = existingUserByEmail.get();
            if (user.getProvider() == null && user.getProviderId() == null) {
                // 일반 계정 -> OAuth로 업데이트
                user.setProvider(provider);
                user.setProviderId(providerId);
                updateUserDetails(user, name, gender, Phone, birthDate);
                userRepository.save(user);
                return new CustomSecurityUserDetails(user, oAuth2User.getAttributes());
            }
            // 이미 OAuth 계정이면 그대로 반환 (중복 가입 방지)
            return new CustomSecurityUserDetails(user, oAuth2User.getAttributes());
        }
        // 3. 전화번호 중복 체크 (phone이 null이 아닌 경우에만)
        if (Phone != null) {
            Optional<SiteUser> existingUserByPhone = userRepository.findByPhone(Phone);
            if (existingUserByPhone.isPresent()) {
                SiteUser user = existingUserByPhone.get();
                if (user.getProvider() == null && user.getProviderId() == null) {
                    // 일반 계정 -> OAuth로 업데이트
                    user.setProvider(provider);
                    user.setProviderId(providerId);
                    updateUserDetails(user, name, gender, Phone, birthDate);
                    userRepository.save(user);
                    return new CustomSecurityUserDetails(user, oAuth2User.getAttributes());
                }
                // 이미 OAuth 계정이면 그대로 반환 (중복 가입 방지)
                return new CustomSecurityUserDetails(user, oAuth2User.getAttributes());
            }
        }
        // 4. 신규 OAuth 사용자 생성
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
                .uuid(String.valueOf(UUID.randomUUID()))
                .build();
        userRepository.save(newUser);
        return new CustomSecurityUserDetails(newUser, oAuth2User.getAttributes());
    }
    // 사용자 세부 정보 업데이트 헬퍼 메서드
    private void updateUserDetails(SiteUser user, String name, String gender, String phone, LocalDate birthDate) {
        if (name != null && !name.isEmpty()) user.setName(name);
        if (gender != null) user.setGender(gender);
        if (phone != null) user.setPhone(phone);
        if (birthDate != null) user.setAge(getAge(birthDate));
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
            String kakaoGender = kakaoAccount != null ? (String) kakaoAccount.get("gender") : null;
            if ("male".equalsIgnoreCase(kakaoGender)) {
                return "남자";
            } else if ("female".equalsIgnoreCase(kakaoGender)) {
                return "여자";
            }
            return null; // 확인 불가 시 null
        } else if ("naver".equals(provider)) {
            Map<String, Object> response = oAuth2User.getAttribute("response");
            String naverGender = response != null ? (String) response.get("gender") : null;
            if ("M".equalsIgnoreCase(naverGender)) {
                return "남자";
            } else if ("F".equalsIgnoreCase(naverGender)) {
                return "여자";
            }
            return null; // "U" 또는 null 시 null
        }
        return null;
    }

    private String getPhoneNumber(OAuth2User oAuth2User, String provider) {
        String rawPhoneNumber = null;
        if ("kakao".equals(provider)) {
            Map<String, Object> kakaoAccount = oAuth2User.getAttribute("kakao_account");
            rawPhoneNumber = kakaoAccount != null ? (String) kakaoAccount.get("phone_number") : null;
        } else if ("naver".equals(provider)) {
            Map<String, Object> response = oAuth2User.getAttribute("response");
            rawPhoneNumber = response != null ? (String) response.get("mobile") : null;
        }
        if (rawPhoneNumber != null) {
            return rawPhoneNumber.replaceAll("[^0-9+]", ""); // 하이픈 및 기타 문자 제거
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