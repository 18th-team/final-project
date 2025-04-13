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
                .sender(type == MessageType.SYSTEM ? null : sender) // 시스템 메시지면 sender null
                .content(content)
                .type(type)
                .timestamp(LocalDateTime.now())
                .build();
        ChatMessage savedMessage = chatMessageRepository.save(message);

        chatRoom.setLastMessage(content);
        chatRoom.setLastMessageTime(savedMessage.getTimestamp());
        chatRoomRepository.save(chatRoom);

        if (type == MessageType.NORMAL) {
            chatRoom.getParticipants().forEach(participant -> {
                if (!participant.equals(sender)) {
                    long unreadCount = chatMessageRepository.findByChatRoomOrderByTimestampAsc(chatRoom)
                            .stream()
                            .filter(msg -> msg.getSender() != null && !msg.getSender().equals(participant)) // null 체크 추가
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
                .filter(msg -> msg.getSender() != null && !msg.getSender().getUuid().equals(user.getUuid())) // null 체크
                .filter(msg -> !msg.getReadBy().contains(user))
                .toList();

        unreadMessages.forEach(msg -> {
            msg.getReadBy().add(user);
            chatMessageRepository.save(msg);
        });

        // long을 int로 캐스팅
        long unreadCount = chatMessageRepository.findByChatRoomOrderByTimestampAsc(chatRoom)
                .stream()
                .filter(msg -> msg.getSender() != null && !msg.getSender().getUuid().equals(user.getUuid())) // null 체크 추가
                .filter(msg -> !msg.getReadBy().contains(user))
                .count();
        return (int) unreadCount; // 캐스팅
    }
}

