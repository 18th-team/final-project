package com.team.moim;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Club {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // 모임 ID (PK)

    private String hostId; // 호스트 ID (작성자 정보)
    private String hostNickname; // 호스트 닉네임

    private String title; // 모임 제목
    private String description; // 모임 소개글

    private int minParticipants; // 최소 인원
    private int maxParticipants; // 최대 인원

    private String ageLimit; // 나이 제한 (예: "20세 이상", "30세 이상" 등)

    private boolean hasFee; // 참가비 여부 (true: 있음, false: 없음)
    private int feeAmount; // 참가비 금액 (없으면 0)
    private String feeType; // 참가비 정보 (노쇼방지비, 대관료 등)

    private String locationType; // 장소 유형 (오프라인 / 온라인)
    private String locationCity; // 도시 (오프라인인 경우)
    private String locationDistrict; // 군/구 (오프라인인 경우)

    private String category; // 모임 주제 (푸드/드링크, 취미 등)

    private LocalDateTime eventDate; // 모임 날짜
    private String eventTime; // 모임 시간 (10분 단위, 오전/오후 포함)

    private LocalDateTime createdAt; // 작성 날짜 및 시간
    private LocalDateTime updatedAt; // 수정 날짜 및 시간

    private LocalDateTime deadline; // 모집 마감 날짜
    private int currentParticipants; // 현재 참여 인원

    @Column(nullable = false)
    private boolean isFull;  // 모집 마감 여부

    private String status; // 모집 상태 (모집 중 / 마감됨)

    private boolean requiresApproval; // 참여 승인 여부 (true: 호스트 승인 필요, false: 자동 승인)

    // Getter, Setter 추가
    public boolean getIsFull() {
        return isFull;
    }

    public void setIsFull(boolean isFull) {
        this.isFull = isFull;
    }

}
