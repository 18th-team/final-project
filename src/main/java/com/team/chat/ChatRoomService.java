package com.team.chat;

import com.team.user.CustomSecurityUserDetails;
import com.team.user.SiteUser;
import com.team.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
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
            chatRoomRepository.delete(chatRoom); // 참여자가 없으면 채팅방 삭제
        } else {
            chatRoom.setStatus("CLOSED");
            chatMessageService.createMessage(chatRoom, user, user.getName() + "님이 채팅방을 떠났습니다.", MessageType.SYSTEM);
            chatRoomRepository.save(chatRoom);
        }

        // 이벤트 발생
        eventPublisher.publishEvent(new ChatRoomUpdateEvent(chatRoom.getRequester().getUuid(), chatRoom.getOwner().getUuid()));
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
        validateInputs(userDetails, receiverUuid, reason);
        SiteUser requester = userRepository.findByUuidWithBlockedUsers(userDetails.getSiteUser().getUuid())
                .orElseThrow(() -> new IllegalArgumentException("요청자를 찾을 수 없습니다: " + userDetails.getSiteUser().getUuid()));
        SiteUser receiver = userRepository.findByUuidWithBlockedUsers(receiverUuid)
                .orElseThrow(() -> new IllegalArgumentException("수신자를 찾을 수 없습니다: " + receiverUuid));

        if (requester.getUuid().equals(receiverUuid)) {
            throw new IllegalArgumentException("자기 자신에게 채팅 요청을 보낼 수 없습니다.");
        }
        if (requester.getBlockedUsers().contains(receiver)) {
            throw new IllegalStateException("차단한 사용자에게 채팅을 요청할 수 없습니다.");
        }
        if (receiver.getBlockedUsers().contains(requester)) {
            throw new IllegalStateException("상대방이 당신을 차단했습니다.");
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
                .unreadCount(0)
                .build();

        ChatRoom savedChatRoom = chatRoomRepository.save(chatRoom);
        eventPublisher.publishEvent(new ChatRoomUpdateEvent(requester.getUuid(), receiver.getUuid()));
        return savedChatRoom;
    }

    @Transactional
    public void handleChatRequest(SiteUser currentUser, Long chatRoomId, String action) {
        validateInputs(currentUser, chatRoomId, action);
        ChatRoom chatRoom = validateChatRoom(chatRoomId, currentUser);
        SiteUser managedUser = userRepository.findByUuid(currentUser.getUuid())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + currentUser.getUuid()));

        String upperAction = action.toUpperCase();
        switch (upperAction) {
            case "APPROVE":
                approveChatRequest(chatRoom, managedUser);
                break;
            case "REJECT":
                chatRoomRepository.delete(chatRoom);
                break;
            case "BLOCK":
                blockChatRequest(chatRoom, managedUser);
                break;
            default:
                throw new IllegalArgumentException("지원하지 않는 액션: " + action);
        }
        eventPublisher.publishEvent(new ChatRoomUpdateEvent(chatRoom.getRequester().getUuid(), managedUser.getUuid()));
    }

    @Transactional(readOnly = true)
    public List<ChatRoomDTO> getChatRoomsForUser(SiteUser user, boolean includeMessages) {
        List<ChatRoom> chatRooms = chatRoomRepository.findByParticipantsContainingOrPendingForUser(user);
        return chatRooms.stream().map(chat -> {
            ChatRoomDTO dto = convertToChatRoomDTO(chat);
            if (includeMessages) {
                dto.setMessages(chatMessageRepository.findByChatRoomOrderByTimestampAsc(chat)
                        .stream().map(this::convertToChatMessageDTO).collect(Collectors.toList()));
            }
            return dto;
        }).collect(Collectors.toList());
    }

    private void validateInputs(Object... inputs) {
        for (Object input : inputs) {
            if (input == null || (input instanceof String && ((String) input).trim().isEmpty())) {
                throw new IllegalArgumentException("입력값이 유효하지 않습니다.");
            }
        }
    }

    private ChatRoom validateChatRoom(Long chatRoomId, SiteUser user) {
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다: " + chatRoomId));
        if (!chatRoom.getOwner().getEmail().equals(user.getEmail())) {
            throw new SecurityException("권한이 없습니다. 사용자: " + user.getUuid() + ", 채팅방: " + chatRoomId);
        }
        if (!"PENDING".equals(chatRoom.getStatus())) {
            throw new IllegalStateException("PENDING 상태에서만 처리 가능: " + chatRoom.getStatus());
        }
        return chatRoom;
    }

    private void approveChatRequest(ChatRoom chatRoom, SiteUser user) {
        chatRoom.setStatus("ACTIVE");
        if (chatRoom.getParticipants().stream().noneMatch(p -> p.getUuid().equals(user.getUuid()))) {
            chatRoom.getParticipants().add(user);
        }
        chatMessageService.createMessage(chatRoom, user, user.getName() + "님이 채팅을 수락하셨습니다.", MessageType.SYSTEM);
        chatRoomRepository.save(chatRoom);
    }

    private void blockChatRequest(ChatRoom chatRoom, SiteUser user) {
        SiteUser blocked = chatRoom.getRequester();
        if (blocked == null) {
            throw new IllegalArgumentException("차단할 요청자가 없습니다.");
        }
        if (user.getBlockedUsers().contains(blocked)) {
            throw new IllegalStateException("이미 차단된 사용자입니다.");
        }
        user.blockUser(blocked);
        userRepository.save(user);
        chatRoomRepository.delete(chatRoom);
    }

    private ChatRoomDTO convertToChatRoomDTO(ChatRoom chat) {
        ChatRoomDTO dto = new ChatRoomDTO();
        dto.setId(chat.getId());
        dto.setName(chat.getName());
        dto.setType(chat.getType());
        dto.setLastMessage(chat.getLastMessage());
        dto.setLastMessageTime(chat.getLastMessageTime());
        dto.setParticipants(chat.getParticipants().stream().map(p -> new ChatRoomDTO.SiteUserDTO(p.getUuid(), p.getName())).collect(Collectors.toList()));
        dto.setOwner(toSiteUserDTO(chat.getOwner()));
        dto.setRequester(toSiteUserDTO(chat.getRequester()));
        dto.setUnreadCount(chat.getUnreadCount());
        dto.setRequestReason(chat.getRequestReason());
        dto.setStatus(chat.getStatus());
        chatMessageRepository.findTopByChatRoomOrderByTimestampDesc(chat).ifPresent(msg -> {
            dto.setLastMessage(msg.getContent());
            dto.setLastMessageTime(msg.getTimestamp());
        });
        return dto;
    }

    private ChatRoomDTO.SiteUserDTO toSiteUserDTO(SiteUser user) {
        return user == null ? null : new ChatRoomDTO.SiteUserDTO(user.getUuid(), user.getName());
    }

    public ChatRoomDTO.ChatMessageDTO convertToChatMessageDTO(ChatMessage msg) {
        ChatRoomDTO.ChatMessageDTO dto = new ChatRoomDTO.ChatMessageDTO();
        dto.setId(msg.getId());
        dto.setChatRoomId(msg.getChatRoom().getId());
        dto.setSender(msg.getSender() != null ? new ChatRoomDTO.SiteUserDTO(msg.getSender().getUuid(), msg.getSender().getName()) : null);
        dto.setContent(msg.getContent());
        dto.setTimestamp(msg.getTimestamp().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        dto.setType(msg.getType().name());
        return dto;
    }
}

class ChatRoomUpdateEvent {
    private final String requesterUuid;
    private final String ownerUuid;

    public ChatRoomUpdateEvent(String requesterUuid, String ownerUuid) {
        this.requesterUuid = requesterUuid;
        this.ownerUuid = ownerUuid;
    }

    public String getRequesterUuid() { return requesterUuid; }
    public String getOwnerUuid() { return ownerUuid; }
}