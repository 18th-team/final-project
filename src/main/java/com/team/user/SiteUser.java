package com.team.user;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SiteUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column
    private String password;

    @Column(nullable = false)
    private Integer age;

    @Column(nullable = false)
    private String gender;

    @Column(unique = true)
    private String phone;

    @Column
    private String profileImage;

    @Column(nullable = false)
    private Integer money = 0;

    @Column(nullable = false)
    private LocalDate createdAt = LocalDate.now();

    @Enumerated(EnumType.STRING)
    private MemberRole role;

    // OAuth 관련 필드
    @Column
    private String provider; // OAuth 제공자 ("kakao", "naver")

    @Column
    private String providerId; // OAuth 제공자에서 발급한 사용자 ID

    @Column(nullable = false, unique = true)
    private String uuid; // 유저 구분

    // 차단 유저 목록 (다대다 관계)
    @ManyToMany
    @JoinTable(
            name = "blocked_users",
            joinColumns = @JoinColumn(name = "blocker_uuid"),
            inverseJoinColumns = @JoinColumn(name = "blocked_uuid")
    )
    private Set<SiteUser> blockedUsers = new HashSet<>();

    // 차단 유저 추가 메서드
    public void blockUser(SiteUser blocked) {
        this.blockedUsers.add(blocked);
    }

    // 차단 해제 메서드
    public void unblockUser(SiteUser blocked) {
        this.blockedUsers.remove(blocked);
    }

    // 내가 특정 사용자에게 차단당했는지 확인
    public boolean isBlockedBy(SiteUser other) {
        return other.blockedUsers.contains(this);
    }
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SiteUser siteUser = (SiteUser) o;
        return uuid != null && uuid.equals(siteUser.uuid);
    }

    @Override
    public int hashCode() {
        return uuid != null ? uuid.hashCode() : 0;
    }
}