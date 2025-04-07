package com.team.chat;

import lombok.Data;

@Data // Lombok으로 Getter, Setter, toString 등 자동 생성
public class ChatRequestDTO {
    private Long chatRoomId;
    private String action;
}