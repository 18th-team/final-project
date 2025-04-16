package com.team.chat;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PushNotificationDTO {
    private Long chatRoomId;
    private String senderName; // 표시용 이름 (유지 가능)
    private String content;
    private Long timestamp; // epoch milliseconds
    private Long messageId;
    private ChatRoomDTO.SiteUserDTO sender; // <<< 발신자 정보 DTO 추가
}