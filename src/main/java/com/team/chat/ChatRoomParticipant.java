package com.team.chat;

import com.team.user.SiteUser;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@RequiredArgsConstructor
public class ChatRoomParticipant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "chat_room_id")
    private ChatRoom chatRoom;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private SiteUser user;

    private boolean notificationEnabled = true; // 사용자별 알림 설정
    private boolean Expanded = false;  // 사용자별 공지사항 펼침/접힘 상태 (기본값: 접힘)

    // 매개변수 생성자 추가
    public ChatRoomParticipant(ChatRoom chatRoom, SiteUser user, boolean notificationEnabled) {
        this.chatRoom = chatRoom;
        this.user = user;
        this.notificationEnabled = notificationEnabled;
        this.Expanded = false; // 초기값 설정
    }
}