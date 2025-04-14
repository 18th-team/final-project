package com.team.chat;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByChatRoomOrderByTimestampAsc(ChatRoom chatRoom);
    void deleteByChatRoom(ChatRoom chatRoom);
    Optional<ChatMessage> findTopByChatRoomOrderByTimestampDesc(ChatRoom chatRoom);
    Page<ChatMessage> findByChatRoom(ChatRoom chatRoom, Pageable pageable);
    long countByChatRoom(ChatRoom chatRoom);
    Optional<ChatMessage> findById(Long id);
}

