package com.team.moim;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Getter
@Setter
public class ClubOneDayDTO {
    private Long id; //나 자신의 PK
    private Long clubId; //모임ID랑 매칭함
    private LocalDate date;
    private LocalTime time;
    private Boolean isOnline;
    private Integer minParticipants;
    private Integer maxParticipants;
    private String place; //자세한 장소/(킨디..)
    private Boolean hasFee;
    private String feeDetails; //참가비 디테일 (input text으로)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
