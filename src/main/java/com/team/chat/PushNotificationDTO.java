package com.team.chat;

import lombok.Data;

@Data
public class PushNotificationDTO {
    private Long chatRoomId;
    private String senderName;
    private String content;
    private Long timestamp;

    public PushNotificationDTO(Long chatRoomId, String senderName, String content, Long timestamp) {
        this.chatRoomId = chatRoomId;
        this.senderName = senderName;
        this.content = content;
        this.timestamp = timestamp;
    }
}