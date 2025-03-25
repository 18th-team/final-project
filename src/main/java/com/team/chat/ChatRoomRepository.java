package com.team.chat;

import com.team.user.SiteUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {
 boolean existsByRequesterEmailAndReceiverEmailAndType(String requesterEmail, String receiverEmail, String type);

 @Query("SELECT c FROM ChatRoom c WHERE :user MEMBER OF c.participants OR (c.status = 'PENDING' AND (c.requesterEmail = :email OR c.receiverEmail = :email))")
 List<ChatRoom> findByParticipantsContainingOrPendingForUser(@Param("user") SiteUser user, @Param("email") String email);
}