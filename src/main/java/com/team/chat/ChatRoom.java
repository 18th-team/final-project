package com.team.chat;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.annotation.JsonBackReference;
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
    private String requesterEmail;
    private String receiverEmail;
    private String type;
    private String lastMessage;
    private LocalDateTime lastMessageTime;

    @ManyToMany
    @JsonManagedReference
    private List<SiteUser> participants;

    @ManyToOne
    @JsonBackReference
    private SiteUser owner;

    @ManyToOne
    @JsonBackReference
    private SiteUser requester;

    private int unreadCount;
    private String requestReason;
    private String status;
}