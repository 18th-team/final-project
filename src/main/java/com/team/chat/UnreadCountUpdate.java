package com.team.chat;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UnreadCountUpdate {
    private Long chatRoomId;
    private int unreadCount;

}