package com.team.chat;

import com.team.moim.entity.Club;
import com.team.moim.entity.ClubFileEntity;
import com.team.moim.repository.ClubFileRepository;
import com.team.moim.repository.ClubRepository;
import com.team.user.CustomSecurityUserDetails;
import com.team.user.SiteUser;
import com.team.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;
    private final UserRepository userRepository;
    private final ChatMessageService chatMessageService;
    private final ChatMessageRepository chatMessageRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ChatRoomParticipantRepository chatRoomParticipantRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ClubRepository clubRepository;
    private final ClubFileRepository clubFileRepository;

    @Transactional(readOnly = true)
    public ChatRoom findChatRoomById(Long chatRoomId) {
        return chatRoomRepository.findByIdWithParticipantsAndBlockedUsers(chatRoomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다: " + chatRoomId));
    }
    @Transactional
    public void updateChatRoom(ChatRoom chatRoom) {
        chatRoomRepository.save(chatRoom);
    }
    @Transactional
    public void leaveChatRoom(Long chatRoomId, String userUuid) {
        // 채팅방 조회
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다: " + chatRoomId));

        // 사용자 조회
        SiteUser user = userRepository.findByUuidWithBlockedUsers(userUuid)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userUuid));

        // 참여자 확인
        if (!chatRoom.getParticipants().contains(user)) {
            throw new IllegalStateException("이 채팅방의 참여자가 아닙니다.");
        }

        // 참여자 목록에서 사용자 제거
        chatRoom.getParticipants().remove(user);


        if (chatRoom.getParticipants().isEmpty()) {
            chatMessageRepository.deleteByChatRoom(chatRoom);
            chatRoomRepository.delete(chatRoom);
        } else {
            if(chatRoom.getType().equals("PRIVATE")){
                chatRoom.setStatus("CLOSED");
            }
            chatMessageService.createMessage(chatRoom, user, user.getName() + "님이 채팅방을 떠났습니다.", MessageType.SYSTEM);
            chatRoomRepository.save(chatRoom);
        }
        // 이벤트 발행: 영향을 받는 UUID 수집
        Set<String> affectedUuids = chatRoom.getParticipants().stream()
                .map(SiteUser::getUuid)
                .collect(Collectors.toSet());

        // owner 추가 (null 체크)
        if (chatRoom.getOwner() != null) {
            affectedUuids.add(chatRoom.getOwner().getUuid());
        }

        // 모임 채팅이 아닌 경우에만 requester 추가
        if (chatRoom.getClub() == null && chatRoom.getRequester() != null) {
            affectedUuids.add(chatRoom.getRequester().getUuid());
        }

        // 이벤트 발행
        eventPublisher.publishEvent(new ChatRoomUpdateEvent(affectedUuids));
    }

    @Transactional
    public void blockUserInChat(Long chatRoomId, String blockerUuid, String blockedUuid) {
        ChatRoom chatRoom = findChatRoomById(chatRoomId);
        SiteUser blocker = userRepository.findByUuidWithBlockedUsers(blockerUuid)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + blockerUuid));
        SiteUser blocked = userRepository.findByUuidWithBlockedUsers(blockedUuid)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + blockedUuid));

        if (!chatRoom.getParticipants().contains(blocker)) {
            throw new IllegalStateException("이 채팅방의 참여자가 아닙니다.");
        }

        blocker.blockUser(blocked);
        chatRoom.getParticipants().remove(blocker);
        if (chatRoom.getParticipants().isEmpty()) {
            chatMessageRepository.deleteByChatRoom(chatRoom);
            chatRoomRepository.delete(chatRoom);
        } else {
            chatRoom.setStatus("BLOCKED");
            chatMessageService.createMessage(chatRoom, null,
                    blocker.getName() + "님이 " + blocked.getName() + "님을 차단했습니다.", MessageType.SYSTEM);
            chatRoomRepository.save(chatRoom);
        }
        eventPublisher.publishEvent(new ChatRoomUpdateEvent(chatRoom.getRequester().getUuid(), chatRoom.getOwner().getUuid()));
    }
    //채팅방 수동 생성, 자동 생성 고려
    @Transactional
    public ChatRoom CreateMoimChatRoom(Long clubId, String name, String ownerUuid) {
        Optional<SiteUser> userOptional = userRepository.findByUuid(ownerUuid);
        if (!userOptional.isPresent()) {
            throw new IllegalStateException("사용자를 찾을 수 없습니다");
        }
        SiteUser ownerUser = userOptional.get();

        Optional<Club> clubOptional = clubRepository.findById(clubId);
        if (!clubOptional.isPresent()) {
            throw new IllegalStateException("모임을 찾을 수 없습니다");
        }
        Club club = clubOptional.get();

        if (!club.getHost().equals(ownerUser)) {
            throw new IllegalStateException("모임장만 채팅방을 생성할 수 있습니다");
        }

        ChatRoom chatRoom = ChatRoom.builder()
                .type("GROUP")
                .name(name != null && !name.isEmpty() ? name : club.getTitle() + " 채팅방")
                .owner(ownerUser)
                .participants(new ArrayList<>()) // 수정 가능 리스트
                .participantSettings(new ArrayList<>()) // 명시적 초기화
                .club(club)
                .status("ACTIVE")
                .build();

        chatRoom.addParticipant(ownerUser); // participants와 participantSettings 동기화
        return chatRoomRepository.save(chatRoom);
    }
    //나간 사람이 모임 채팅방을 다시 참가 할수 있게 ( 참여한 모임 확인 필요)
    @Transactional
    public void JoinMoimChatRoom(Long chatRoomId, String uuid) {
        // 1. 사용자 확인
        Optional<SiteUser> userOptional = userRepository.findByUuid(uuid);
        if (userOptional.isEmpty()) {
            throw new IllegalStateException("사용자를 찾을 수 없습니다");
        }
        SiteUser joinUser = userOptional.get();

        // 2. 채팅방 확인
        Optional<ChatRoom> chatRoomOptional = chatRoomRepository.findById(chatRoomId);
        if (chatRoomOptional.isEmpty()) {
            throw new IllegalStateException("채팅방이 존재하지 않습니다");
        }
        ChatRoom chatRoom = chatRoomOptional.get();

        // 3. 그룹 채팅방인지 확인
        if (!"GROUP".equals(chatRoom.getType())) {
            throw new IllegalStateException("그룹 채팅방만 참여할 수 있습니다");
        }

        // 4. 모임 멤버인지 확인
        Club club = chatRoom.getClub();
        if (club == null || !club.getMembers().contains(joinUser)) {
            throw new IllegalStateException("해당 모임의 멤버만 채팅방에 참여할 수 있습니다");
        }

        // 5. 이미 참여 중인지 확인
        boolean isAlreadyParticipant = chatRoom.getParticipants().contains(joinUser);
        if (isAlreadyParticipant) {
            throw new IllegalStateException("이미 채팅방에 참여 중입니다");
        }

        // 6. 참여자 추가
        chatRoom.addParticipant(joinUser);
        chatRoomRepository.save(chatRoom);
    }

    @Transactional
    public ChatRoom requestPersonalChat(CustomSecurityUserDetails userDetails, String receiverUuid, String reason) {
        SiteUser requester = userRepository.findByUuidWithBlockedUsers(userDetails.getSiteUser().getUuid())
                .orElseThrow(() -> new IllegalArgumentException("요청자를 찾을 수 없습니다: " + userDetails.getSiteUser().getUuid()));
        SiteUser receiver = userRepository.findByUuidWithBlockedUsers(receiverUuid)
                .orElseThrow(() -> new IllegalArgumentException("수신자를 찾을 수 없습니다: " + receiverUuid));

        if (requester.getUuid().equals(receiverUuid) || requester.getBlockedUsers().contains(receiver) || receiver.getBlockedUsers().contains(requester)) {
            throw new IllegalStateException("채팅 요청이 불가능한 상태입니다.");
        }
        if (chatRoomRepository.existsByRequesterAndOwnerAndTypeAndStatusNot(requester, receiver, "PRIVATE", "CLOSED")) {
            throw new IllegalStateException("이미 존재하는 개인 채팅 요청입니다.");
        }

        ChatRoom chatRoom = ChatRoom.builder()
                .type("PRIVATE")
                .name(receiver.getName())
                .requester(requester)
                .owner(receiver)
                .participants(new ArrayList<>()) // 변경 가능한 리스트
                .participantSettings(new ArrayList<>())
                .requestReason(reason)
                .status("PENDING")
                .lastMessageTime(LocalDateTime.now())
                .build();
        chatRoom.addParticipant(requester);
        chatRoom.addParticipant(receiver);
        ChatRoom savedChatRoom = chatRoomRepository.save(chatRoom);
        // 이벤트 발행
        eventPublisher.publishEvent(new ChatRoomUpdateEvent(requester.getUuid(), receiver.getUuid()));
        return savedChatRoom;
    }

    @Transactional
    public void handleChatRequest(SiteUser currentUser, Long chatRoomId, String action) {
        ChatRoom chatRoom = validateChatRoom(chatRoomId, currentUser);
        String requesterUuid = chatRoom.getRequester().getUuid();
        String ownerUuid = chatRoom.getOwner().getUuid();
        switch (action.toUpperCase()) {
            case "APPROVE":
                System.out.println("승인 처리 시작: chatRoomId=" + chatRoomId);
                chatRoom.setStatus("ACTIVE");
                if (!chatRoom.getParticipants().contains(currentUser)) {
                    chatRoom.getParticipants().add(currentUser);
                }
                chatMessageService.createMessage(chatRoom, currentUser, currentUser.getName() + "님이 채팅을 수락하셨습니다.", MessageType.SYSTEM);
                ChatRoom savedChatRoom = chatRoomRepository.save(chatRoom);
                System.out.println("저장된 채팅방 상태: " + savedChatRoom.getStatus());
                // 데이터베이스 직접 확인
                ChatRoom dbChatRoom = chatRoomRepository.findById(chatRoomId).orElse(null);
                System.out.println("DB에서 조회한 상태: " + (dbChatRoom != null ? dbChatRoom.getStatus() : "없음"));
                break;
            case "REJECT":
            case "BLOCK":
                if ("BLOCK".equals(action.toUpperCase())) {
                    SiteUser blocked = chatRoom.getRequester();
                    currentUser.blockUser(blocked);
                    userRepository.save(currentUser);
                }
                chatRoomRepository.delete(chatRoom);
                break;
            default:
                throw new IllegalArgumentException("지원하지 않는 액션: " + action);
        }
        System.out.println("이벤트 발행: requester=" + requesterUuid + ", owner=" + ownerUuid);
        eventPublisher.publishEvent(new ChatRoomUpdateEvent(requesterUuid, ownerUuid));
    }

    @Transactional(readOnly = true)
    public List<ChatRoomDTO> getChatRoomsForUser(String userUuid) {
        SiteUser user = userRepository.findByUuidWithBlockedUsers(userUuid)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userUuid));
        List<ChatRoom> chatRooms = chatRoomRepository.findByParticipantsContainingOrPendingForUser(user);
        return chatRooms.stream().map(chat -> {
            ChatRoomDTO dto = convertToChatRoomDTO(chat);
            long unreadCount = chatMessageRepository.findByChatRoomOrderByTimestampAsc(chat)
                    .stream()
                    .filter(msg -> msg.getSender() != null && !msg.getSender().getUuid().equals(user.getUuid())) // null 체크 추가
                    .filter(msg -> msg.getReadBy() == null || !msg.getReadBy().contains(user))
                    .count();
            dto.setUnreadCount((int) unreadCount);

            Optional<ChatMessage> lastMessageOpt = chatMessageRepository.findTopByChatRoomOrderByTimestampDesc(chat);
            if (lastMessageOpt.isPresent()) {
                ChatMessage lastMessage = lastMessageOpt.get();
                ChatRoomDTO.SenderDTO senderDTO = new ChatRoomDTO.SenderDTO();
                if (lastMessage.getType() == MessageType.SYSTEM || lastMessage.getSender() == null) { // 시스템 메시지 또는 null 처리
                    senderDTO.setUuid(null);
                    senderDTO.setName(null);
                } else {
                    senderDTO.setUuid(lastMessage.getSender().getUuid());
                    senderDTO.setName(lastMessage.getSender().getName());
                }
                senderDTO.setLastMessage(lastMessage.getContent());
                senderDTO.setLastMessageTime(lastMessage.getTimestamp());
                dto.setLastMessageSender(senderDTO);
            } else {
                dto.setLastMessageSender(null);
            }

            ChatRoomParticipant participant = chatRoomParticipantRepository.findByChatRoomAndUser(chat, user)
                    .orElseThrow(() -> new IllegalStateException("참여자를 찾을 수 없습니다."));
            dto.setNotificationEnabled(participant.isNotificationEnabled());
            dto.setExpanded(participant.isExpanded());
            return dto;
        }).collect(Collectors.toList());
    }
    @Transactional(readOnly = true)
    public List<ChatRoomDTO.ChatMessageDTO> getMessages(ChatRoom chatRoom, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("timestamp").ascending());
        Page<ChatMessage> messagePage = chatMessageRepository.findByChatRoom(chatRoom, pageable);
        return messagePage.getContent().stream()
                .map(this::convertToChatMessageDTO)
                .collect(Collectors.toList());
    }

    private ChatRoom validateChatRoom(Long chatRoomId, SiteUser user) {
        System.out.println("유효성 검사: chatRoomId=" + chatRoomId + ", userUuid=" + user.getUuid());
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> {
                    System.out.println("채팅방 없음: " + chatRoomId);
                    return new IllegalArgumentException("채팅방을 찾을 수 없습니다: " + chatRoomId);
                });
        System.out.println("채팅방 상태=" + chatRoom.getStatus() + ", 소유자=" + chatRoom.getOwner().getUuid());
        if (!chatRoom.getOwner().getUuid().equals(user.getUuid()) || !"PENDING".equals(chatRoom.getStatus())) {
            System.out.println("검사 실패: 소유자=" + chatRoom.getOwner().getUuid().equals(user.getUuid()) + ", 상태=" + "PENDING".equals(chatRoom.getStatus()));
            throw new SecurityException("권한이 없습니다.");
        }
        return chatRoom;
    }

    private ChatRoomDTO convertToChatRoomDTO(ChatRoom chat) {
        ChatRoomDTO dto = new ChatRoomDTO();
        dto.setId(chat.getId());
        dto.setName(chat.getName());
        dto.setType(chat.getType());
        dto.setParticipants(chat.getParticipants().stream()
                .map(p -> new ChatRoomDTO.SiteUserDTO(p.getUuid(), p.getName(), p.getProfileImage())) // profileImage 추가
                .collect(Collectors.toList()));
        dto.setOwner(chat.getOwner() != null ?
                new ChatRoomDTO.SiteUserDTO(chat.getOwner().getUuid(), chat.getOwner().getName(), chat.getOwner().getProfileImage()) : null); // profileImag
        dto.setRequester(chat.getRequester() != null ?
                new ChatRoomDTO.SiteUserDTO(chat.getRequester().getUuid(), chat.getRequester().getName(), chat.getRequester().getProfileImage()) : null); // profileImage 추가
        dto.setRequestReason(chat.getRequestReason());
        dto.setStatus(chat.getStatus());

        // 그룹 채팅방의 경우 Club 이미지 추가
        if ("GROUP".equals(chat.getType()) && chat.getClub() != null && chat.getClub().getFileAttached() == 1) {
            Optional<ClubFileEntity> clubFile = clubFileRepository.findByClubId(chat.getClub().getId());
            clubFile.ifPresent(file -> dto.setClubImage(file.getStoredFileName()));
        }

        return dto;
    }

    public ChatRoomDTO.ChatMessageDTO convertToChatMessageDTO(ChatMessage msg) {
        ChatRoomDTO.ChatMessageDTO dto = new ChatRoomDTO.ChatMessageDTO();
        dto.setId(msg.getId());
        dto.setChatRoomId(msg.getChatRoom().getId());
        dto.setSender(msg.getSender() != null ?
            new ChatRoomDTO.SiteUserDTO(msg.getSender().getUuid(), msg.getSender().getName(), msg.getSender().getProfileImage()) : null); // profileImage 추가
        dto.setContent(msg.getContent());
        dto.setTimestamp(msg.getTimestamp());
        dto.setType(msg.getType().name());
        return dto;
    }

    @EventListener(ChatRoomUpdateEvent.class)
    public void handleChatRoomUpdate(ChatRoomUpdateEvent event) {
        for (String uuid : event.getAffectedUuids()) {
            List<ChatRoomDTO> chatRooms = getChatRoomsForUser(uuid);
            messagingTemplate.convertAndSend("/user/" + uuid + "/topic/chatrooms", chatRooms);
            System.out.println("Sent /topic/chatrooms to UUID: " + uuid + ", ChatRooms: " + chatRooms);
        }
    }
}