package com.team.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    // 채팅방의 메시지를 시간순으로 조회
    List<ChatMessage> findByChatRoomOrderByTimestampAsc(ChatRoom chatRoom);

    // 채팅방의 마지막 메시지 조회
    Optional<ChatMessage> findTopByChatRoomOrderByTimestampDesc(ChatRoom chatRoom);

    // 채팅방의 최신 2개 메시지 조회
    List<ChatMessage> findTop2ByChatRoomOrderByTimestampDesc(ChatRoom chatRoom);
}
