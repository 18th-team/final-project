package com.team.moim.entity;
//원데이모임,

import com.team.moim.ClubDTO;
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

    @Column(name = "host_id") //호스트아이디
    private Long hostId;

    @Column(name = "host_name") //호스트이름
    private String hostName;


    //todo DTO -> Entity로 저장
    public static Club toSaveEntity(ClubDTO clubDTO) {
        Club clubEntity = new Club();
        clubEntity.setTitle(clubDTO.getTitle());
        clubEntity.setContent(clubDTO.getContent());
        clubEntity.setCity(clubDTO.getCity());
        clubEntity.setDistrict(clubDTO.getDistrict());
        clubEntity.setAgeRestriction(clubDTO.getAgeRestriction());
        clubEntity.setTheme(clubDTO.getTheme());
        clubEntity.setHostId(clubDTO.getHostId());
        clubEntity.setHostName(clubDTO.getHostName());
        return clubEntity;
    }

    public static Club toUpdateEntity(ClubDTO clubDTO) {
        Club clubEntity = new Club();
        clubEntity.setId(clubDTO.getId());
        clubEntity.setTitle(clubDTO.getTitle());
        clubEntity.setContent(clubDTO.getContent());
        clubEntity.setCity(clubDTO.getCity());
        clubEntity.setDistrict(clubDTO.getDistrict());
        clubEntity.setAgeRestriction(clubDTO.getAgeRestriction());
        clubEntity.setTheme(clubDTO.getTheme());
        clubEntity.setHostId(clubDTO.getHostId());
        clubEntity.setHostName(clubDTO.getHostName());
        return clubEntity;
    }
}