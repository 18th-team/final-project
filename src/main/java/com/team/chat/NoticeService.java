package com.team.chat;

import com.team.user.SiteUser;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class NoticeService {
    private final NoticeRepository noticeRepository;
    private final ChatRoomParticipantRepository chatRoomParticipantRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final SimpMessagingTemplate messagingTemplate;

    // 공지사항 생성
    public void createNotice(Long chatRoomId, String content, SiteUser user) {
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다."));
        validateGroupChatAndOwnership(chatRoom, user);
        if (noticeRepository.existsByChatRoom(chatRoom)) {
            throw new IllegalStateException("이미 공지사항이 존재합니다.");
        }

        Notice notice = new Notice(chatRoom, content);
        noticeRepository.save(notice);

        broadcastNoticeUpdate(chatRoom, notice, "CREATED");
    }

    // 공지사항 수정
    public void updateNotice(Long chatRoomId, String content, SiteUser user) {
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다."));
        validateGroupChatAndOwnership(chatRoom, user);
        Notice notice = noticeRepository.findByChatRoom(chatRoom)
                .orElseThrow(() -> new IllegalStateException("공지사항이 존재하지 않습니다."));
        notice.setContent(content);
        notice.setUpdatedAt(LocalDateTime.now());
        noticeRepository.save(notice);

        broadcastNoticeUpdate(chatRoom, notice, "UPDATED");
    }

    // 공지사항 삭제
    public void deleteNotice(Long chatRoomId, SiteUser user) {
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다."));
        validateGroupChatAndOwnership(chatRoom, user);
        Notice notice = noticeRepository.findByChatRoom(chatRoom)
                .orElseThrow(() -> new IllegalStateException("공지사항이 존재하지 않습니다."));
        noticeRepository.delete(notice);

        broadcastNoticeUpdate(chatRoom, null, "DELETED");
    }

    // 공지사항 조회
    @Transactional(readOnly = true)
    public NoticeDTO getNotice(Long chatRoomId, SiteUser user) {
        ChatRoom chatRoom = chatRoomRepository.findByIdWithParticipantSettings(chatRoomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다."));

        boolean isParticipant = chatRoom.getParticipants().stream()
                .anyMatch(p -> p.getUuid().equals(user.getUuid()));
        if (!isParticipant) {
            throw new SecurityException("채팅방에 접근할 권한이 없습니다.");
        }
        if (!"GROUP".equals(chatRoom.getType())) {
            return new NoticeDTO(null, chatRoomId, null, null, null, false);
        }

        Notice notice = noticeRepository.findByChatRoom(chatRoom).orElse(null);
        boolean isExpanded = chatRoom.getParticipantSettings().stream()
                .filter(ps -> ps.getUser().getUuid().equals(user.getUuid()))
                .findFirst()
                .map(ChatRoomParticipant::isNoticeExpanded)
                .orElse(false);

        NoticeDTO noticeDTO = notice != null
                ? new NoticeDTO(notice.getId(), notice.getChatRoom().getId(), notice.getContent(), notice.getCreatedAt(), notice.getUpdatedAt(), isExpanded)
                : new NoticeDTO(null, chatRoomId, null, null, null, isExpanded);
        noticeDTO.setOwner(chatRoom.getOwner().getUuid().equals(user.getUuid()));
        return noticeDTO;
    }

    // 펼침/접힘 상태 토글
    @Transactional
    public void toggleNoticeState(Long chatRoomId, boolean isExpanded, SiteUser user) {
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다."));
        ChatRoomParticipant participant = chatRoomParticipantRepository.findByChatRoomAndUser(chatRoom, user)
                .orElseThrow(() -> new IllegalStateException("참여자 정보가 존재하지 않습니다."));

        System.out.println("Before update: isNoticeExpanded=" + participant.isNoticeExpanded());
        participant.setNoticeExpanded(isExpanded);
        System.out.println("After set: isNoticeExpanded=" + participant.isNoticeExpanded());

        ChatRoomParticipant saved = chatRoomParticipantRepository.saveAndFlush(participant);
        System.out.println("Saved entity: isNoticeExpanded=" + saved.isNoticeExpanded());

        // 데이터베이스 직접 확인
        ChatRoomParticipant updated = chatRoomParticipantRepository.findByChatRoomAndUser(chatRoom, user)
                .orElseThrow(() -> new IllegalStateException("업데이트 후 조회 실패"));
        boolean savedState = updated.isNoticeExpanded();
        System.out.println("Saved notice state for user " + user.getUuid() + " in chatRoom " + chatRoomId + ": " + savedState);

        Map<String, Object> payload = new HashMap<>();
        payload.put("chatRoomId", chatRoomId);
        payload.put("expanded", savedState);
        System.out.println("Sending payload: " + payload);
        messagingTemplate.convertAndSend("/user/" + user.getUuid() + "/topic/noticeState", payload);
    }
    private void validateGroupChatAndOwnership(ChatRoom chatRoom, SiteUser user) {
        if (!"GROUP".equals(chatRoom.getType())) {
            throw new IllegalArgumentException("그룹 채팅방에서만 공지사항을 관리할 수 있습니다.");
        }
        if (!chatRoom.getOwner().getUuid().equals(user.getUuid())) {
            throw new SecurityException("모임장만 공지사항을 관리할 수 있습니다.");
        }
    }

    private void broadcastNoticeUpdate(ChatRoom chatRoom, Notice notice, String action) {
        chatRoom.getParticipants().forEach(participant -> {
            ChatRoomParticipant chatRoomParticipant = chatRoomParticipantRepository.findByChatRoomAndUser(chatRoom, participant)
                    .orElseThrow(() -> new IllegalStateException("참여자 정보가 존재하지 않습니다."));
            boolean isExpanded = chatRoomParticipant.isNoticeExpanded();
            NoticeDTO noticeDTO = (notice != null)
                    ? new NoticeDTO(notice.getId(), notice.getChatRoom().getId(), notice.getContent(), notice.getCreatedAt(), notice.getUpdatedAt(), isExpanded)
                    : new NoticeDTO(null, chatRoom.getId(), null, null, null, isExpanded);
            messagingTemplate.convertAndSend("/user/" + participant.getUuid() + "/topic/notice", noticeDTO);
        });
    }
}