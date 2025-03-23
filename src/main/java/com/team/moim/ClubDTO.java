package com.team.moim;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ClubDTO {
    private Long id;
    private String title;
    private String content;
    private String images01;
    private String images02;
    private String images03;
    private String city; //모임장소 (시) 수원시
    private String district; //모임장소 (군/구  ( option 으로 입력받기 -> 나중에 키워드 매칭 )

    private String ageRestriction; //나이-세이상
    private String theme; //카테고리
    private Long hostId;
    private String hostName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;}
