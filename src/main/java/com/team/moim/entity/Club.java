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
    private String city;

    @Column
    private String district;

    @Column
    private String ageRestriction;

    @ManyToOne
    @JoinColumn(name = "host_id")
    private SiteUser host;

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


    @OneToMany(mappedBy = "club", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private List<ClubFileEntity> clubFileEntityList = new ArrayList<>();

    @ManyToMany
    @JoinTable(
            name = "club_keyword",
            joinColumns = @JoinColumn(name = "club_id"),
            inverseJoinColumns = @JoinColumn(name = "keyword_id")
    )
    private Set<Keyword> keywords = new HashSet<>();

    // 가입자 추가
    @ManyToMany
    @JoinTable(
            name = "club_member",
            joinColumns = @JoinColumn(name = "club_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private Set<SiteUser> members = new HashSet<>();

    // ChatRoom과의 관계 추가
    @OneToOne(mappedBy = "club", cascade = CascadeType.ALL, orphanRemoval = true)
    private ChatRoom chatRoom;

    // DTO -> Entity로 저장 (기본)
    public static Club toSaveEntity(ClubDTO clubDTO, SiteUser host, Set<Keyword> keywords) {
        return Club.builder()
                .title(clubDTO.getTitle())
                .content(clubDTO.getContent())
                .city(clubDTO.getCity())
                .district(clubDTO.getDistrict())
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

    // DTO -> Entity로 업데이트
    public static Club toUpdateEntity(ClubDTO clubDTO, SiteUser host, Set<Keyword> keywords) {
        return Club.builder()
                .id(clubDTO.getId())
                .title(clubDTO.getTitle())
                .content(clubDTO.getContent())
                .city(clubDTO.getCity())
                .district(clubDTO.getDistrict())
                .ageRestriction(clubDTO.getAgeRestriction())
                .host(host)
                .keywords(keywords)
                .build();
    }

    // 파일 포함 저장
    public static Club toSaveFileEntity(ClubDTO clubDTO, SiteUser host, Set<Keyword> keywords) {
        return Club.builder()
                .title(clubDTO.getTitle())
                .content(clubDTO.getContent())
                .city(clubDTO.getCity())
                .district(clubDTO.getDistrict())
                .ageRestriction(clubDTO.getAgeRestriction())
                .host(host)
                .fileAttached(1)
                .keywords(keywords)
                .location(clubDTO.getLocation())
                .locationTitle(clubDTO.getLocationTitle())
                .latitude(clubDTO.getLatitude())
                .longitude(clubDTO.getLongitude())
                .build();
    }

    // 파일 포함 업데이트 (기존 clubFileEntityList 유지)
    public static Club toUpdateFileEntity(ClubDTO clubDTO, String location, String locationTitle, Double latitude, Double longitude, SiteUser host, Club existingClub, Set<Keyword> keywords) {
        return Club.builder()
                .id(existingClub.getId())
                .title(clubDTO.getTitle())
                .content(clubDTO.getContent())
                .city(clubDTO.getCity())
                .district(clubDTO.getDistrict())
                .ageRestriction(clubDTO.getAgeRestriction())
                .host(host)
                .fileAttached(clubDTO.getClubFile() != null && !clubDTO.getClubFile().stream().allMatch(MultipartFile::isEmpty) ? 1 : existingClub.getFileAttached())
                .clubFileEntityList(existingClub.getClubFileEntityList()) // 기존 파일 리스트 유지
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