package com.team.chat;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MarkMessageReadRequest {
    private Long chatRoomId;
    private Long messageId;
}