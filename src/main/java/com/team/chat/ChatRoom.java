package com.team.chat;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.team.user.SiteUser;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatRoom {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name; // 개인 채팅: null 또는 사용자 이름 조합, 그룹 채팅: 그룹 이름
    private String type; // "PRIVATE" 또는 "GROUP"

    private String lastMessage;
    private LocalDateTime lastMessageTime;

    @ManyToMany
    @JsonManagedReference
    @Builder.Default
    private List<SiteUser> participants = new ArrayList<>(); // 참여자 목록

    @ManyToOne
    @JsonBackReference
    private SiteUser owner; // 개인: 채팅 받는 사람, 그룹: 모임장

    @ManyToOne
    @JsonBackReference
    private SiteUser requester; // 개인 채팅에서만 사용 (요청자)

    private int unreadCount;
    private String requestReason;
    private String status; // PENDING, ACTIVE, REJECTED, BLOCKED 등

    // 참여자 추가 메서드
    public void addParticipant(SiteUser user) {
        if (!participants.contains(user)) {
            participants.add(user);
        }
    }

    // 참여자 제거 메서드
    public void removeParticipant(SiteUser user) {
        participants.remove(user);
    }
}