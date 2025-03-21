package com.team.user;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

public class CustomSecurityUserDetails implements UserDetails, OAuth2User {

    private final SiteUser siteUser;
    private Map<String, Object> attributes; // OAuth2User에서 필요한 속성 맵

    // 폼 로그인용 생성자
    public CustomSecurityUserDetails(SiteUser siteUser) {
        this.siteUser = siteUser;
        this.attributes = null; // 폼 로그인에서는 attributes가 필요 없음
    }
    // 소셜 로그인용 생성자
    public CustomSecurityUserDetails(SiteUser siteUser, Map<String, Object> attributes) {
        this.siteUser = siteUser;
        this.attributes = attributes;
    }

    // UserDetails 구현
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + siteUser.getRole().name()));
    }

    @Override
    public String getPassword() {
        return siteUser.getPassword();
    }

    @Override
    public String getUsername() {
        return siteUser.getEmail();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    // OAuth2User 구현
    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public String getName() {
        return siteUser.getProviderId(); // 소셜 로그인 사용자 식별자
    }

    public SiteUser getSiteUser() {
        return siteUser;
    }
}