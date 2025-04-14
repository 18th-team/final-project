package com.team.chat;

import lombok.Data;

@Data
class NoticeRequestDTO {
    private Long chatRoomId;
    private String content;
}