package com.team.chat;

import com.team.user.SiteUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChatRoomParticipantRepository extends JpaRepository<ChatRoomParticipant, Long> {
    Optional<ChatRoomParticipant> findByChatRoomAndUser(ChatRoom chatRoom, SiteUser user);
}