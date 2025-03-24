package com.team.moim;

import com.team.moim.entity.Club;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

@Getter
@Setter
public class ClubDTO {
    private Long id;
    private String title;
    private String content;
    private String city; // 모임 장소 (시)
    private String district; // 모임 장소 (군/구)
    private String ageRestriction; // 나이 제한 (예: "20세 이상")
    private String theme; // 카테고리
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 작성자 정보
    private Long hostId;    // 작성자의 ID
    private String hostName; // 작성자의 이름

    //note 단일 이미지 파일 받기
private MultipartFile clubFile;
private String originalFileName;
private String storedFileName;
private int fileAttached;//파일 첨부 여부(1:첨부,0:미첨부)

    // Entity -> DTO로 변환
    public static ClubDTO toDTO(Club club) {
        ClubDTO clubDTO = new ClubDTO();
        clubDTO.setId(club.getId());
        clubDTO.setTitle(club.getTitle());
        clubDTO.setContent(club.getContent());
        clubDTO.setCity(club.getCity());
        clubDTO.setDistrict(club.getDistrict());
        clubDTO.setAgeRestriction(club.getAgeRestriction());
        clubDTO.setTheme(club.getTheme());
        clubDTO.setCreatedAt(club.getCreatedAt()); // BaseEntity에서 상속
        clubDTO.setUpdatedAt(club.getUpdatedAt()); // BaseEntity에서 상속
       //note 로그인한사용자 세팅하기
        if (club.getHost() != null) {
            clubDTO.setHostId(club.getHost().getId());
            clubDTO.setHostName(club.getHost().getName());
        }

        return clubDTO;
    }
}