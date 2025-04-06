package com.team.chat;

import com.team.user.SiteUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {
 // 개인 채팅 중복 체크: requester와 owner 기준
 boolean existsByRequesterAndOwnerAndType(SiteUser requester, SiteUser owner, String type);

 // 사용자가 참여 중이거나 PENDING 상태인 채팅방 조회
 @Query("SELECT c FROM ChatRoom c WHERE :user MEMBER OF c.participants OR (c.status = 'PENDING' AND (c.requester = :user OR c.owner = :user))")
 List<ChatRoom> findByParticipantsContainingOrPendingForUser(@Param("user") SiteUser user);
 boolean existsByRequesterAndOwnerAndTypeAndStatusNot(SiteUser requester, SiteUser owner, String type, String status);
 @Query("SELECT cr FROM ChatRoom cr " +
         "JOIN FETCH cr.participants p " +
         "LEFT JOIN FETCH p.blockedUsers " +
         "WHERE cr.id = :id")
 Optional<ChatRoom> findByIdWithParticipantsAndBlockedUsers(@Param("id") Long id);

 @Query("SELECT DISTINCT cr FROM ChatRoom cr JOIN FETCH cr.participants p WHERE p.uuid = :uuid")
 List<ChatRoom> findRoomsAndFetchParticipantsByUserUuid(@Param("uuid") String uuid);
}