package com.team.user;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@NoArgsConstructor // 기본 생성자
@AllArgsConstructor // 모든 필드 포함 생성자
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
    private List<SiteUser> blockedUsers = new ArrayList<>();

    // 차단 유저 추가 메서드
    public void blockUser(SiteUser blocked) {
        if (!blockedUsers.contains(blocked)) {
            blockedUsers.add(blocked);
        }
    }

    // 차단 해제 메서드
    public void unblockUser(SiteUser blocked) {
        blockedUsers.remove(blocked);
    }
}
