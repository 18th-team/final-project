package com.team.chat;

import com.team.chat.ChatMessage;
import com.team.chat.ChatMessageRepository;
import com.team.chat.ChatRoom;
import com.team.chat.MessageType;
import com.team.user.SiteUser;
import com.team.user.UserRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatMessageService {
    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final SimpMessagingTemplate messagingTemplate;
    @Transactional
    public ChatMessage createMessage(ChatRoom chatRoom, SiteUser sender, String content, MessageType type) {
        ChatMessage message = ChatMessage.builder()
                .chatRoom(chatRoom)
                .sender(sender)
                .content(content)
                .type(type)
                .timestamp(LocalDateTime.now())
                .build();
        ChatMessage savedMessage = chatMessageRepository.save(message);

        // 일반 메시지일 경우 unreadCount 업데이트
        if (type == MessageType.NORMAL) {
            chatRoom.getParticipants().forEach(participant -> {
                if (!participant.equals(sender)) { // 발신자는 제외
                    long unreadCount = chatMessageRepository.findByChatRoomOrderByTimestampAsc(chatRoom)
                            .stream()
                            .filter(msg -> !msg.getSender().equals(participant))
                            .filter(msg -> !msg.getReadBy().contains(participant))
                            .count();
                    messagingTemplate.convertAndSend(
                            "/user/" + participant.getUuid() + "/topic/readUpdate",
                            new UnreadCountUpdate(chatRoom.getId(), (int) unreadCount)
                    );
                }
            });
        }
        return savedMessage;
    }
    @Transactional
    public int markMessagesAsRead(ChatRoom chatRoom, SiteUser user) {
        List<ChatMessage> unreadMessages = chatMessageRepository.findByChatRoomOrderByTimestampAsc(chatRoom)
                .stream()
                .filter(msg -> !msg.getSender().getUuid().equals(user.getUuid()))
                .filter(msg -> !msg.getReadBy().contains(user))
                .toList();

        unreadMessages.forEach(msg -> {
            msg.getReadBy().add(user);
            chatMessageRepository.save(msg);
        });

        // long을 int로 캐스팅
        long unreadCount = chatMessageRepository.findByChatRoomOrderByTimestampAsc(chatRoom)
                .stream()
                .filter(msg -> !msg.getSender().getUuid().equals(user.getUuid()))
                .filter(msg -> !msg.getReadBy().contains(user))
                .count();
        return (int) unreadCount; // 캐스팅 추가
    }
}

