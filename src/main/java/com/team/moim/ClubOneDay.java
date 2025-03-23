package com.team.moim;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Data
public class ClubOneDay {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; //나 자신의 PK

    private Long clubId; //모임ID랑 매칭함

    @Column(name = "event_date") //모임날짜
    private LocalDate date;

    @Column(name = "event_time") //모임시간
    private LocalTime time;

@Column(name = "is_online")
private Boolean isOnline;

    @Column(name = "min_participants") //최소인원
    private Integer minParticipants;

    @Column(name = "max_participants") //최대인원
    private Integer maxParticipants;

@Column
    private Integer place; //자세한 장소/(킨디..)

    @Column(name = "has_fee") //참가비여부
    private Boolean hasFee;

    @Column(name = "fee_amount") //참가비
    private Integer feeAmount;

    private String feeDetails; //참가비 디테일 (input text으로)

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
