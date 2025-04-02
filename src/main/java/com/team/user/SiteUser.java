package com.team.user;

import com.team.moim.entity.Club;
import com.team.moim.entity.Keyword;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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

    // 새로 추가할 자기소개 컬럼
    @Column(length = 50, nullable = false) // 50자 제한
    private String introduction;

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

    // 양방향 관계 추가
    @OneToMany(mappedBy = "host", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Club> hostedClubs = new ArrayList<>();

    // 편의 메서드 (선택 사항)
    public void addHostedClub(Club club) {
        hostedClubs.add(club);
        club.setHost(this);
    }

    // 키워드 연결 추가
    @ManyToMany
    @JoinTable(
            name = "user_keyword",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "keyword_id")
    )
    private Set<Keyword> keywords = new HashSet<>();

    @ManyToMany(mappedBy = "members")
    private Set<Club> clubs = new HashSet<>();
    // Getter, Setter
    public Set<Club> getClubs() {
        return clubs;
    }

    public void joinClub(Club club) {
        clubs.add(club);
    }
}
