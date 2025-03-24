package com.team.moim.entity;
//원데이모임,

import com.team.moim.ClubDTO;
import com.team.user.SiteUser;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "club")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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
    private String city; //모임장소 (서울,수원)
    @Column
    private String district; //모임장소 (군/구  ( option 으로 입력받기 -> 나중에 키워드 매칭 )
    @Column
    private String ageRestriction; //나이-세이상

    @Column
    private String theme; //카테고리

    /*
    * todo 외래키로 연결하기
    *  */
    @ManyToOne
    @JoinColumn(name = "host_id") // 외래키로 host_id 컬럼 사용
    private SiteUser host; // 로그인한 사용자 (SiteUser와 관계)


    //note DTO -> Entity로 저장
    public static Club toSaveEntity(ClubDTO clubDTO, SiteUser host) {
        return Club.builder()
                .title(clubDTO.getTitle())
                .content(clubDTO.getContent())
                .city(clubDTO.getCity())
                .district(clubDTO.getDistrict())
                .ageRestriction(clubDTO.getAgeRestriction())
                .theme(clubDTO.getTheme())
                .host(host) // 로그인한 사용자를 host로 설정
                .build();
    }

    //note  DTO -> Entity로 업데이트
    public static Club toUpdateEntity(ClubDTO clubDTO, SiteUser host) {
        return Club.builder()
                .id(clubDTO.getId())
                .title(clubDTO.getTitle())
                .content(clubDTO.getContent())
                .city(clubDTO.getCity())
                .district(clubDTO.getDistrict())
                .ageRestriction(clubDTO.getAgeRestriction())
                .theme(clubDTO.getTheme())
                .host(host) // 업데이트 시에도 host 유지
                .build();
    }

    //note hostName을 동적으로 가져오는 메서드 (필요 시)
    public String getHostName() {
        return host != null ? host.getName() : null;
    }
}