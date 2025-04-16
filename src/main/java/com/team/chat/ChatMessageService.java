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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatMessageService {
    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomRepository chatRoomRepository;
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

        // 마지막 메시지 및 시간 갱신
        chatRoom.setLastMessage(content);
        chatRoom.setLastMessageTime(savedMessage.getTimestamp());
        chatRoomRepository.save(chatRoom);

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
    @Transactional
    public int markMessageAsRead(ChatRoom chatRoom, SiteUser user, Long messageId) {
        System.out.println("Attempting to mark message as read: chatRoomId=" + chatRoom.getId() +
                ", userUuid=" + user.getUuid() +
                ", messageId=" + messageId);
        ChatMessage message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("메시지를 찾을 수 없습니다: " + messageId));

        // 메시지가 해당 채팅방에 속하는지 확인
        if (!message.getChatRoom().getId().equals(chatRoom.getId())) {
            throw new IllegalArgumentException("메시지가 이 채팅방에 속하지 않습니다.");
        }

        // 이미 읽음 처리된 경우 스킵
        if (!message.getReadBy().contains(user)) {
            message.getReadBy().add(user);
            chatMessageRepository.save(message);
        }

        // 읽지 않은 메시지 수 계산
        long unreadCount = chatMessageRepository.findByChatRoomOrderByTimestampAsc(chatRoom)
                .stream()
                .filter(msg -> msg.getSender() != null && !msg.getSender().getUuid().equals(user.getUuid()))
                .filter(msg -> !msg.getReadBy().contains(user))
                .count();
        return (int) unreadCount;
    }
}

