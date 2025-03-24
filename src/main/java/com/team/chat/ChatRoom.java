package com.team.chat;

import com.team.user.SiteUser;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Getter
@Setter
public class ChatRoom {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name; // 개인: 상대방 이름, 모임: 모임 이름
    private String type; // "PERSONAL" 또는 "GROUP"

    private String lastMessage; // 마지막 메시지
    private LocalDateTime lastMessageTime; // 마지막 메시지 시간

    @ManyToMany
    private List<SiteUser> participants; // 참여자 목록

    @ManyToOne
    private SiteUser owner; // 모임 채팅의 경우 모임장 (PERSONAL에서는 null)

    private int unreadCount; // 미확인 메시지 수

    private String requestReason; // 개인: 요청 이유, 모임: 가입 요청 이유
    private String status; // PENDING, APPROVED, REJECTED, BLOCKED
}
