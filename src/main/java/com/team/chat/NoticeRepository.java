package com.team.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NoticeRepository extends JpaRepository<Notice, Long> {
    Optional<Notice> findByChatRoom(ChatRoom chatRoom);
    boolean existsByChatRoom(ChatRoom chatRoom);
}
