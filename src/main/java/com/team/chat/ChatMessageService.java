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
        message.setTimestamp(LocalDateTime.now());
        message.setType(type);
        message.setReadBy(new HashSet<>()); // 빈 Set으로 초기화, sender 추가 안 함
        return chatMessageRepository.save(message);
    }

}