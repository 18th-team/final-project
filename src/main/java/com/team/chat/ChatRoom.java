package com.team.chat;

import com.team.user.SiteUser;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
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

    private String name;
    private String requesterEmail; // 수정: RequesterEmail -> requesterEmail
    private String receiverEmail;
    private String type;
    private String lastMessage;
    private LocalDateTime lastMessageTime;

    @ManyToMany
    private List<SiteUser> participants;

    @ManyToOne
    private SiteUser owner;

    @ManyToOne
    private SiteUser requester;

    private int unreadCount;
    private String requestReason;
    private String status;
}