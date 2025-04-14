package com.team.chat;

import lombok.Data;

@Data
public class PushNotificationDTO {
    private Long chatRoomId;
    private String senderName;
    private String content;
    private Long timestamp;
    private Long messageId; // 추가

    public PushNotificationDTO(Long chatRoomId, String senderName, String content, Long timestamp, Long messageId) {
        this.chatRoomId = chatRoomId;
        this.senderName = senderName;
        this.content = content;
        this.timestamp = timestamp;
        this.messageId = messageId;
    }
}