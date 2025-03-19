package com.team.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder

public class NewMoim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 참여 인원
    @Column(nullable = false)
    private Integer minParticipants; // 최소 인원

    @Column(nullable = false)
    private Integer maxParticipants; // 최대 인원

    // 나이 제한 애너테이션 확인 필요
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AgeRestriction ageRestriction; // 나이 ENUM

    // 참가비
    @Column(nullable = false)
    private Boolean hasFee; // 참가비 여부

    @Column
    private Integer feeAmount; // 참가비 금액 (있을 경우)


    @ElementCollection
    @CollectionTable(name = "moim_fee_details", joinColumns = @JoinColumn(name = "moim_id"))
    @Column(name = "fee_detail")
    @Enumerated(EnumType.STRING)

    private List<FeeDetail> feeDetails = new ArrayList<>();
    // 참가비 세부 정보 (다중 선택 가능)

    // 장소
    @Column(nullable = false)
    private Boolean isOnline; // 온라인 여부

    @Enumerated(EnumType.STRING)
    @Column
    private City city;

    @Column
    private String district;

    // 모임 주제
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MoimTheme moimTheme; // 모임 주제 ENUM

    // 사진 (최대 3장)
    @ElementCollection
    @CollectionTable(name = "moim_images", joinColumns = @JoinColumn(name = "moim_id"))
    @Column(name = "image_path")
    private List<String> images = new ArrayList<>(); // 이미지 경로 리스트

    // 모임 제목 (최소 5글자)
    @Column(nullable = false, length = 100)
    private String title;

    // 모임 소개글 (최소 10글자)
    @Column(nullable = false, length = 500)
    private String content;

    // 모임 날짜
    @Column(nullable = false)
    private LocalDate date;

    // 모임 시간
    @Column(nullable = false)
    private LocalTime time;
}
