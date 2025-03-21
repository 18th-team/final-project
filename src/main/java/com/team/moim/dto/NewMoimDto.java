package com.team.moim.dto;

import com.team.moim.entity.AgeRestriction;
import com.team.moim.entity.City;
import com.team.moim.entity.FeeDetail;
import com.team.moim.entity.MoimTheme;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class NewMoimDto {
    private Long id;
    private City city;
    private String cityKoreanName; // 추가
    private Integer minParticipants;
    private Integer maxParticipants;
    private AgeRestriction ageRestriction;
    private Boolean hasFee;
    private Integer feeAmount;
    private List<FeeDetail> feeDetails = new ArrayList<>();
    private Boolean isOnline;
    private Long districtId;    // District 엔티티 대신 ID로
    private String districtName; // 뷰에서 보여줄 이름 (선택)
    private MoimTheme moimTheme;
    private List<MultipartFile> imageFiles; // 업로드용 파일
    private List<String> images = new ArrayList<>(); // 저장된 경로
    private String title;
    private String content;
    private LocalDate date;
    private LocalTime time;

    // 엔티티 -> DTO 변환용 생성자
    public NewMoimDto(com.team.moim.entity.NewMoim moim) {
        this.id = moim.getId();
        this.city = moim.getCity();
        this.cityKoreanName = moim.getCity() != null ? moim.getCity().getKoreanName() : null;
        this.minParticipants = moim.getMinParticipants();
        this.maxParticipants = moim.getMaxParticipants();
        this.ageRestriction = moim.getAgeRestriction();
        this.hasFee = moim.getHasFee();
        this.feeAmount = moim.getFeeAmount();
        this.feeDetails = moim.getFeeDetails();
        this.isOnline = moim.getIsOnline();
        this.districtId = moim.getDistrict() != null ? moim.getDistrict().getId() : null;
        this.districtName = moim.getDistrict() != null ? moim.getDistrict().getName() : null; // 이름 설정
        this.moimTheme = moim.getMoimTheme();
        this.images = moim.getImages();
        this.title = moim.getTitle();
        this.content = moim.getContent();
        this.date = moim.getDate();
        this.time = moim.getTime();
    }
}