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
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatMessageService {
    private final ChatMessageRepository chatMessageRepository;

    @Transactional
    public ChatMessage createMessage(ChatRoom chatRoom, SiteUser sender, String content, MessageType type) {
        ChatMessage message = new ChatMessage();
        message.setChatRoom(chatRoom);
        message.setSender(sender);
        message.setContent(content);
        message.setTimestamp(LocalDateTime.now());
        message.setType(type);
        return chatMessageRepository.save(message);
    }
    @Transactional
    public void deleteMessagesByChatRoomId(Long chatRoomId) {
        // chatRoomId로 메시지 조회
        List<ChatMessage> messages = chatMessageRepository.findByChatRoomId(chatRoomId);
        if (!messages.isEmpty()) {
            // 메시지 삭제
            chatMessageRepository.deleteAll(messages);
            System.out.println("Deleted " + messages.size() + " messages for chatRoomId: " + chatRoomId);
        } else {
            System.out.println("No messages found for chatRoomId: " + chatRoomId);
        }
    }
}