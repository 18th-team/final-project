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
    private Map<String, Object> attributes;

    // 폼 로그인용 생성자
    public CustomSecurityUserDetails(SiteUser siteUser) {
        this.siteUser = siteUser;
        this.attributes = null;
    }

    // 소셜 로그인용 생성자
    public CustomSecurityUserDetails(SiteUser siteUser, Map<String, Object> attributes) {
        System.out.println(siteUser);
        this.siteUser = siteUser;
        this.attributes = attributes;
    }

    // UserDetails 구현
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        String role = (siteUser.getRole() != null) ? siteUser.getRole().name() : "USER";
        System.out.println("Authorities: ROLE_" + role);
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getPassword() {
        return siteUser.getPassword();
    }

    @Override
    public String getUsername() {
        return siteUser.getUuid(); // UUID 반환
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
        return siteUser.getUuid(); // UUID 반환
    }

    public SiteUser getSiteUser() {
        return siteUser;
    }
}