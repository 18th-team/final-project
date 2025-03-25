package com.team.chat;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@RequiredArgsConstructor
public class ChatRoomDTO {
    private Long id;
    private String name;
    private String type;
    private String lastMessage;
    private LocalDateTime lastMessageTime;
    private List<String> participants;
    private String owner;
    private int unreadCount;
    private String requestReason;
    private String status;
    private String requesterEmail;
}