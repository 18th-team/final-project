package com.team.moim;

import com.team.moim.entity.Club;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ClubDTO {
    private Long id;
    private String title;
    private String content;
    private String city; //모임장소 (시) 수원시
    private String district; //모임장소 (군/구  ( option 으로 입력받기 -> 나중에 키워드 매칭 )

    private String ageRestriction; //나이-세이상
    private String theme; //카테고리
    private Long hostId;
    private String hostName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /*
     * todo
     *  entity -> DTO로 변환
     * */

    public static ClubDTO toDTO(Club club) {
        ClubDTO clubDTO = new ClubDTO();
        clubDTO.setId(club.getId());
        clubDTO.setTitle(club.getTitle());
        clubDTO.setContent(club.getContent());
        clubDTO.setCity(club.getCity());
        clubDTO.setDistrict(club.getDistrict());
        clubDTO.setAgeRestriction(club.getAgeRestriction());
        clubDTO.setTheme(club.getTheme());
        clubDTO.setHostId(club.getHostId());
        clubDTO.setHostName(club.getHostName());
        clubDTO.setCreatedAt(club.getCreatedAt());
        clubDTO.setUpdatedAt(club.getUpdatedAt());
        return clubDTO;

    }
}
