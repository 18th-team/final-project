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
 boolean existsByRequesterAndOwnerAndType(SiteUser requester, SiteUser owner, String type);

 @Query("SELECT c FROM ChatRoom c WHERE :user MEMBER OF c.participants OR (c.status = 'PENDING' AND (c.requester = :user OR c.owner = :user))")
 List<ChatRoom> findByParticipantsContainingOrPendingForUser(@Param("user") SiteUser user);

 boolean existsByRequesterAndOwnerAndTypeAndStatusNot(SiteUser requester, SiteUser owner, String type, String status);

 @Query("SELECT cr FROM ChatRoom cr " +
         "JOIN FETCH cr.participants p " +
         "LEFT JOIN FETCH p.blockedUsers " +
         "WHERE cr.id = :id")
 Optional<ChatRoom> findByIdWithParticipantsAndBlockedUsers(@Param("id") Long id);

 @Query("SELECT DISTINCT cr FROM ChatRoom cr JOIN FETCH cr.participants WHERE cr IN (SELECT c FROM ChatRoom c JOIN c.participants p WHERE p.uuid = :uuid)")
 List<ChatRoom> findRoomsAndFetchParticipantsByUserUuid(@Param("uuid") String uuid);

 @Query("SELECT cr FROM ChatRoom cr " +
         "LEFT JOIN FETCH cr.participantSettings ps " +
         "LEFT JOIN FETCH ps.user " +
         "WHERE cr.id = :id")
 Optional<ChatRoom> findByIdWithParticipantSettings(@Param("id") Long id);

}