package com.team.user;

import com.team.moim.entity.Club;
import com.team.moim.entity.Keyword;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Set;

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

    // 새로 추가할 자기소개 컬럼 (null 허용)
    @Column(length = 50) // 50자 제한
    private String introduction;

    @Column(nullable = false, unique = true)
    private String email;

    @Column
    private String password;

    @Column(nullable = false)
    private LocalDate birthdate;
    //age <- LocalDate birthdate로 변경 나이 제한의 경우 birthdate 기준으로 계산해서 확인 하게

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

    // 양방향 관계 추가
    @OneToMany(mappedBy = "host", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Club> hostedClubs = new ArrayList<>();



    // 키워드 연결 추가
    @ManyToMany
    @JoinTable(
            name = "user_keyword",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "keyword_id")
    )
    private Set<Keyword> keywords = new HashSet<>();

    //클럽가입관계추가
    //가입된 모임 <- 모임 채팅방 출력 할떄 사용 ?
    @ManyToMany(mappedBy = "members")
    private Set<Club>clubs = new HashSet<>();

    @Override
    public String toString() {
        return "SiteUser{id=" + id + ", email=" + email + "}";
    }
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


    @Column
    private LocalDateTime lastOnline;


    // 차단 유저 추가 메서드
    public void blockUser(SiteUser blocked) {
        this.blockedUsers.add(blocked);
    }

    // 차단 해제 메서드
    public void unblockUser(SiteUser blocked) {
        this.blockedUsers.remove(blocked);
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