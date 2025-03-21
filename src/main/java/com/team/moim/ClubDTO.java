package com.team.moim;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ClubDTO {
    private Long id;
    private String title;
    private String description;
    private String hostId;
    private String hostNickname;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String status;  // 모집 중 / 마감됨
    private boolean isFull;
    private int currentParticipants;
    private int minParticipants;
    private int maxParticipants;
}