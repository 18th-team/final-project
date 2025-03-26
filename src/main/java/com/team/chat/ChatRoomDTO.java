package com.team.chat;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ChatRoomDTO {
    private Long id;
    private String name;
    private String type;
    private String lastMessage;
    private LocalDateTime lastMessageTime;
    private List<SiteUserDTO> participants;
    private SiteUserDTO owner;
    private SiteUserDTO requester;
    private int unreadCount;
    private String requestReason;
    private String status;

    @Data
    public static class SiteUserDTO {
        private String uuid;
        private String name;
    }
}