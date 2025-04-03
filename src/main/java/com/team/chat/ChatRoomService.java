package com.team.chat;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

    @Transactional(readOnly = true)
    public ChatRoom findChatRoomById(Long chatRoomId) {
        return chatRoomRepository.findByIdWithParticipantsAndBlockedUsers(chatRoomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다: " + chatRoomId));
    }

    @Transactional
    public void leaveChatRoom(Long chatRoomId, String userUuid) {
        ChatRoom chatRoom = findChatRoomById(chatRoomId);
        SiteUser user = userRepository.findByUuidWithBlockedUsers(userUuid)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userUuid));

        if (!chatRoom.getParticipants().contains(user)) {
            throw new IllegalStateException("이 채팅방의 참여자가 아닙니다.");
        }

        chatRoom.getParticipants().remove(user);
        if (chatRoom.getParticipants().isEmpty()) {
            chatMessageRepository.deleteByChatRoom(chatRoom);
            chatRoomRepository.delete(chatRoom);
        } else {
            chatRoom.setStatus("CLOSED");
            chatMessageService.createMessage(chatRoom, user, user.getName() + "님이 채팅방을 떠났습니다.", MessageType.SYSTEM);
            chatRoomRepository.save(chatRoom);
        }
        // 모든 참가자에게 이벤트 발행
        Set<String> affectedUuids = chatRoom.getParticipants().stream()
                .map(SiteUser::getUuid)
                .collect(Collectors.toSet());
        affectedUuids.add(chatRoom.getRequester().getUuid());
        affectedUuids.add(chatRoom.getOwner().getUuid());
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
                .participants(List.of(requester))
                .requestReason(reason)
                .status("PENDING")
                .lastMessageTime(LocalDateTime.now())
                .build();

        ChatRoom savedChatRoom = chatRoomRepository.save(chatRoom);
        ChatRoomParticipant requesterParticipant = new ChatRoomParticipant(savedChatRoom, requester, true);
        ChatRoomParticipant ownerParticipant = new ChatRoomParticipant(savedChatRoom, receiver, true);
        savedChatRoom.addParticipantSetting(requesterParticipant);
        savedChatRoom.addParticipantSetting(ownerParticipant);
        chatRoomParticipantRepository.save(requesterParticipant);
        chatRoomParticipantRepository.save(ownerParticipant);

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
                chatRoom.setStatus("ACTIVE");
                if (!chatRoom.getParticipants().contains(currentUser)) {
                    chatRoom.getParticipants().add(currentUser);
                }
                chatMessageService.createMessage(chatRoom, currentUser, currentUser.getName() + "님이 채팅을 수락하셨습니다.", MessageType.SYSTEM);
                chatRoomRepository.save(chatRoom);
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
        eventPublisher.publishEvent(new ChatRoomUpdateEvent(requesterUuid, ownerUuid));
    }

    @Transactional(readOnly = true)
    public List<ChatRoomDTO> getChatRoomsForUser(SiteUser user) {
        List<ChatRoom> chatRooms = chatRoomRepository.findByParticipantsContainingOrPendingForUser(user);
        return chatRooms.stream().map(chat -> {
            ChatRoomDTO dto = convertToChatRoomDTO(chat);
            long unreadCount = chatMessageRepository.findByChatRoomOrderByTimestampAsc(chat)
                    .stream()
                    .filter(msg -> !msg.getSender().getUuid().equals(user.getUuid()))
                    .filter(msg -> msg.getReadBy() == null || !msg.getReadBy().contains(user))
                    .count();
            dto.setUnreadCount((int) unreadCount);

            chatMessageRepository.findTopByChatRoomOrderByTimestampDesc(chat).ifPresent(msg -> {
                dto.setLastMessage(msg.getContent());
                dto.setLastMessageTime(msg.getTimestamp().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
            });

            ChatRoomParticipant participant = chatRoomParticipantRepository.findByChatRoomAndUser(chat, user)
                    .orElseThrow(() -> new IllegalStateException("참여자를 찾을 수 없습니다."));
            dto.setNotificationEnabled(participant.isNotificationEnabled());
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
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다: " + chatRoomId));
        if (!chatRoom.getOwner().getUuid().equals(user.getUuid()) || !"PENDING".equals(chatRoom.getStatus())) {
            throw new SecurityException("권한이 없습니다.");
        }
        return chatRoom;
    }

    private ChatRoomDTO convertToChatRoomDTO(ChatRoom chat) {
        ChatRoomDTO dto = new ChatRoomDTO();
        dto.setId(chat.getId());
        dto.setName(chat.getName());
        dto.setType(chat.getType());
        dto.setLastMessage(chat.getLastMessage());
        dto.setLastMessageTime(chat.getLastMessageTime() != null ?
                chat.getLastMessageTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() : null);
        dto.setParticipants(chat.getParticipants().stream()
                .map(p -> new ChatRoomDTO.SiteUserDTO(p.getUuid(), p.getName()))
                .collect(Collectors.toList()));
        dto.setOwner(chat.getOwner() != null ?
                new ChatRoomDTO.SiteUserDTO(chat.getOwner().getUuid(), chat.getOwner().getName()) : null);
        dto.setRequester(chat.getRequester() != null ?
                new ChatRoomDTO.SiteUserDTO(chat.getRequester().getUuid(), chat.getRequester().getName()) : null);
        dto.setRequestReason(chat.getRequestReason());
        dto.setStatus(chat.getStatus());
        chatMessageRepository.findTopByChatRoomOrderByTimestampDesc(chat).ifPresent(msg -> {
            dto.setLastMessage(msg.getContent());
            dto.setLastMessageTime(msg.getTimestamp().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        });
        return dto;
    }

    public ChatRoomDTO.ChatMessageDTO convertToChatMessageDTO(ChatMessage msg) {
        ChatRoomDTO.ChatMessageDTO dto = new ChatRoomDTO.ChatMessageDTO();
        dto.setId(msg.getId());
        dto.setChatRoomId(msg.getChatRoom().getId());
        dto.setSender(msg.getSender() != null ?
                new ChatRoomDTO.SiteUserDTO(msg.getSender().getUuid(), msg.getSender().getName()) : null);
        dto.setContent(msg.getContent());
        dto.setTimestamp(msg.getTimestamp().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        dto.setType(msg.getType().name());
        return dto;
    }

    @EventListener(ChatRoomUpdateEvent.class)
    public void handleChatRoomUpdate(ChatRoomUpdateEvent event) {
        for (String uuid : event.getAffectedUuids()) {
            userRepository.findByUuid(uuid).ifPresent(user -> {
                List<ChatRoomDTO> chatRooms = getChatRoomsForUser(user);
                messagingTemplate.convertAndSend("/user/" + uuid + "/topic/chatrooms", chatRooms);
            });
        }
    }
}