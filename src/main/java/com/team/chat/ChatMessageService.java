package com.team.chat;

import com.team.chat.ChatMessage;
import com.team.chat.ChatMessageRepository;
import com.team.chat.ChatRoom;
import com.team.chat.MessageType;
import com.team.user.SiteUser;
import lombok.RequiredArgsConstructor;
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

    @Transactional
    public ChatMessage createMessage(ChatRoom chatRoom, SiteUser sender, String content, MessageType type) {
        ChatMessage message = new ChatMessage();
        message.setChatRoom(chatRoom);
        message.setSender(sender);
        message.setContent(content);
        message.setType(type);
        message.setTimestamp(java.time.LocalDateTime.now());
        if (sender != null) {
            message.getReadBy().add(sender); // 보낸 사람은 기본적으로 읽음 처리
        }
        return chatMessageRepository.save(message);
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