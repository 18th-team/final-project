package com.team.moim;
//원데이모임,
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "club")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Club {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(length = 1000)
    private String content;

    @Column
    private String images01;

    @Column
    private String images02;

    @Column
    private String images03;
    @Column
    private String city; //모임장소 (시) 수원시
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

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}