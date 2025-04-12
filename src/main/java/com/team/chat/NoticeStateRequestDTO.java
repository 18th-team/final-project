package com.team.chat;

import lombok.Data;

@Data
public class NoticeStateRequestDTO {
    private Long chatRoomId;
    private boolean expanded;
}