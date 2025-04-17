package com.team.moim.entity;

import com.team.chat.ChatRoom;
import com.team.moim.ClubDTO;
import com.team.user.SiteUser;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@Entity
@Table(name = "club")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Club extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(length = 1000)
    private String content;

    @Column
    private String ageRestriction;

    @Column
    private int fileAttached;

    // 주소와 좌표 필드 추가
    @Column(nullable = false)
    private String location; // 주소 (예: "서울특별시 강남구...")

    @Column
    private String locationTitle; // 타이틀: "북수원자이"

    @Column
    private Double latitude; // 위도

    @Column
    private Double longitude; // 경도

    /* note ****** join ************** */
    @ManyToOne
    @JoinColumn(name = "host_id")
    private SiteUser host;

    @OneToMany(mappedBy = "club", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private List<ClubFileEntity> clubFileEntityList = new ArrayList<>();

    @ManyToMany
    @JoinTable(
            name = "club_keyword",
            joinColumns = @JoinColumn(name = "club_id"),
            inverseJoinColumns = @JoinColumn(name = "keyword_id")
    )
    private Set<Keyword> keywords = new HashSet<>();

    @ManyToMany
    @JoinTable(
            name = "club_member",
            joinColumns = @JoinColumn(name = "club_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private Set<SiteUser> members = new HashSet<>();

    @OneToOne(mappedBy = "club", cascade = CascadeType.ALL, orphanRemoval = true)
    private ChatRoom chatRoom;

    // DTO -> Entity로 저장 (기본)
    public static Club toSaveEntity(ClubDTO clubDTO, SiteUser host, Set<Keyword> keywords) {
        return Club.builder()
                .title(clubDTO.getTitle())
                .content(clubDTO.getContent())
                .ageRestriction(clubDTO.getAgeRestriction())
                .host(host)
                .fileAttached(0)
                .keywords(keywords)
                .location(clubDTO.getLocation())
                .locationTitle(clubDTO.getLocationTitle())
                .latitude(clubDTO.getLatitude())
                .longitude(clubDTO.getLongitude())
                .build();
    }


    // 파일 포함 저장
    public static Club toSaveFileEntity(ClubDTO clubDTO, SiteUser host, Set<Keyword> keywords) {
        return Club.builder()
                .title(clubDTO.getTitle())
                .content(clubDTO.getContent())
                .ageRestriction(clubDTO.getAgeRestriction())
                .host(host)
                .fileAttached(1)
                .keywords(keywords)
                .location(clubDTO.getLocation())
                .locationTitle(clubDTO.getLocationTitle())
                .latitude(clubDTO.getLatitude())
                .longitude(clubDTO.getLongitude())
                .members(new HashSet<>()) // 초기화
                .build();
    }

    // 파일 포함 업데이트 (기존 clubFileEntityList 유지)
    public static Club toUpdateFileEntity(ClubDTO clubDTO, String location, String locationTitle,
                                          Double latitude, Double longitude, SiteUser host, Club existingClub, Set<Keyword> keywords) {
        return Club.builder()
                .id(existingClub.getId())
                .title(clubDTO.getTitle())
                .content(clubDTO.getContent())
                .ageRestriction(clubDTO.getAgeRestriction())
                .host(host)
                .fileAttached(existingClub.getFileAttached()) // 파일 상태는 update 메서드에서 처리
                .clubFileEntityList(existingClub.getClubFileEntityList()) // 파일 리스트도 update에서 설정
                .keywords(keywords)
                .location(location)
                .locationTitle(locationTitle)
                .latitude(latitude)
                .longitude(longitude)
                .build();
    }

    @Override
    public String toString() {
        return "Club{id=" + id + ", title=" + title + ", memberCount=" + members.size() + "}";
    }



}