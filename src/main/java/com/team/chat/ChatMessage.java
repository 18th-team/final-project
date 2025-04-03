package com.team.chat;

import com.team.user.SiteUser;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_room_id")
    private ChatRoom chatRoom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id")
    private SiteUser sender; // 메시지 발신자

    @Column(nullable = false)
    private String content; // 메시지 내용

    @Column(nullable = false)
    private LocalDateTime timestamp; // 전송 시각

    @Enumerated(EnumType.STRING)
    private MessageType type = MessageType.NORMAL; // 메시지 타입 (일반, 시스템)

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "chat_message_read_by",
            joinColumns = @JoinColumn(name = "chat_message_id"),
            inverseJoinColumns = @JoinColumn(name = "read_by_id")
    )
    @Builder.Default // 초기값 보장
    private Set<SiteUser> readBy = new HashSet<>();

    // 방어 로직 추가
    public Set<SiteUser> getReadBy() {
        if (readBy == null) {
            readBy = new HashSet<>();
        }
        return readBy;
    }
}
