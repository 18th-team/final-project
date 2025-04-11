package com.team.chat;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NoticeDTO {
    private Long id;
    private Long chatRoomId;
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean Expanded;
    private boolean isOwner; // 추가

    public NoticeDTO(Long id, Long chatRoomId, String content, LocalDateTime createdAt, LocalDateTime updatedAt, boolean Expanded) {
        this.id = id;
        this.chatRoomId = chatRoomId;
        this.content = content;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.Expanded = Expanded;
        this.isOwner = false; // 기본값
    }
}