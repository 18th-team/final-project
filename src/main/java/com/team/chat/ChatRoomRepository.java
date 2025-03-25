package com.team.chat;

import com.team.user.SiteUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {
   /* List<ChatRoom> findByParticipantsContaining(SiteUser user);*/
    ChatRoom findByParticipantsContainingAndName(SiteUser user, String name);
    List<ChatRoom> findByOwner(SiteUser owner); // 모임장이 소유한 채팅방 조회
    ChatRoom findByIdAndOwner(Long id, SiteUser owner); // 특정 모임 채팅 조회
    @Query("SELECT c FROM ChatRoom c JOIN FETCH c.participants p WHERE :user MEMBER OF c.participants")
    List<ChatRoom> findByParticipantsContaining(@Param("user") SiteUser user);
}