package com.team.chat;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ChatRoomDTO {
    private Long id;
    private String name;
    private String type;
    private String lastMessage;
    private Long lastMessageTime;
    private List<SiteUserDTO> participants;
    private SiteUserDTO owner;
    private SiteUserDTO requester;
    private int unreadCount;
    private String requestReason;
    private String status;
    private List<ChatMessageDTO> messages;
    private boolean notificationEnabled;
    private Integer page; // 페이징: 페이지 번호
    private Integer size; // 페이징: 페이지 크기
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SiteUserDTO {
        private String uuid;
        private String name;
    }
    @Data
    public static class ChatMessageDTO {
        private Long id;
        private Long chatRoomId;
        private ChatRoomDTO.SiteUserDTO sender;
        private String content;
        private Long timestamp;
        private String type; // "NORMAL" or "SYSTEM"
    }
}