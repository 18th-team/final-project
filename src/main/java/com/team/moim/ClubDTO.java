package com.team.moim;

import com.team.moim.entity.Club;
import com.team.moim.entity.ClubFileEntity;
import com.team.moim.entity.Keyword;
import com.team.user.SiteUser;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Collectors;

@Getter
@Setter
@ToString
public class ClubDTO {
    private Long id;
    private String title; //모임 제목 <- 채팅 타이틀로 출력
    private String content;
    private String ageRestriction; // 나이 제한 (예: "20세 이상")
    private String selectedTheme; // 클럽 카테고리 선택 (단일 선택)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 작성자 정보
    private Long hostId;    // 작성자의 ID
    private String hostName; // 작성자의 이름
    private String hostImg; // 작성자의 사진
    private String hostIntro; // 작성자의 자기소개
    private List<String> hostSelectedKeywords; // 사용자가 선택한 다중 키워드

    //지도API
    private String location;
    private String locationTitle;
    private Double latitude;
    private Double longitude;
    private Double distance; //사용자 현재 거리

    //note 단일 이미지 파일 받기 --> 다중파일 받기 List
    private List<MultipartFile> clubFile;
    private List<String> originalFileName;
    private List<String> storedFileName;
    private int fileAttached;//파일 첨부 여부(1:첨부,0:미첨부)

    // 새로 추가
// 참여자 정보
    private List<String> memberNames = new ArrayList<>();
    private List<String> memberDescriptions = new ArrayList<>();
    private List<String> memberImages = new ArrayList<>(); // 프로필 사진 리스트로 변경
    private List<String> memberEmails = new ArrayList<>(); // 추가
    private int memberCount; //클럽참여가 되어있는지.


    // Entity -> DTO로 변환
    public static ClubDTO toDTO(Club club) {
        ClubDTO clubDTO = new ClubDTO();
        clubDTO.setId(club.getId());
        clubDTO.setTitle(club.getTitle());
        clubDTO.setContent(club.getContent());
        clubDTO.setAgeRestriction(club.getAgeRestriction());
        clubDTO.setSelectedTheme(club.getKeywords().isEmpty() ? null : club.getKeywords().iterator().next().getName());
        clubDTO.setMemberNames(club.getMembers().stream().map(SiteUser::getName).collect(Collectors.toList()));
        clubDTO.setMemberDescriptions(club.getMembers().stream().map(SiteUser::getIntroduction).collect(Collectors.toList()));
        clubDTO.setMemberCount(club.getMembers().size());
        clubDTO.setCreatedAt(club.getCreatedAt()); // BaseEntity에서 상속
        clubDTO.setUpdatedAt(club.getUpdatedAt()); // BaseEntity에서 상속
        clubDTO.setLocation(club.getLocation());
        clubDTO.setLocationTitle(club.getLocationTitle());
        clubDTO.setLatitude(club.getLatitude());
        clubDTO.setLongitude(club.getLongitude());
        //note 로그인한사용자 세팅하기
        if (club.getHost() != null) {
            clubDTO.setHostId(club.getHost().getId());
            clubDTO.setHostName(club.getHost().getName());
            clubDTO.setHostImg(club.getHost().getProfileImage());
            clubDTO.setHostIntro(club.getHost().getIntroduction());
            // 다중 키워드 매핑
            clubDTO.setHostSelectedKeywords(club.getHost().getKeywords().stream().map(Keyword::getName).collect(Collectors.toList()));
        }

        //note 사진 가져오기
        if (club.getFileAttached() == 0) {
            clubDTO.setFileAttached(club.getFileAttached()); //0이 세팅
        } else {
            List<String> originalFileName = new ArrayList<>();
            List<String> storedFileName = new ArrayList<>();
            clubDTO.setFileAttached(club.getFileAttached()); //1이 세팅
            //반복하여 하나씩 저장하기
            for (ClubFileEntity clubFileEntity : club.getClubFileEntityList()) {
                originalFileName.add(clubFileEntity.getOriginalFilename());
                storedFileName.add(clubFileEntity.getStoredFileName());
            }
            clubDTO.setOriginalFileName(originalFileName);
            clubDTO.setStoredFileName(storedFileName);
            clubDTO.setMemberNames(club.getMembers().stream().map(SiteUser::getName).collect(Collectors.toList()));
            clubDTO.setMemberDescriptions(club.getMembers().stream().map(SiteUser::getIntroduction).collect(Collectors.toList()));
            clubDTO.setMemberCount(club.getMembers().size());
            clubDTO.setMemberImages(club.getMembers().stream().map(SiteUser::getProfileImage).map(img -> img != null ? img : "/img/default-profile.png").collect(Collectors.toList()));
            clubDTO.setMemberEmails(club.getMembers().stream().map(SiteUser::getEmail).collect(Collectors.toList()));

            return clubDTO;
        }
        return clubDTO;
    }
}
