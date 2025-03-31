package com.team.chat;

import com.team.user.CustomSecurityUserDetails;
import com.team.user.SiteUser;
import com.team.user.UserRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
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

    @Transactional(readOnly = true)
    public ChatRoom findChatRoomById(Long chatRoomId) {
        return chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다: " + chatRoomId));
    }

    @Transactional
    public ChatRoom requestPersonalChat(CustomSecurityUserDetails userDetails, String receiverUuid, String reason) {
        if (userDetails == null) {
            throw new IllegalArgumentException("인증된 사용자가 필요합니다.");
        }
        SiteUser requester = userDetails.getSiteUser();
        if (receiverUuid == null || receiverUuid.trim().isEmpty()) {
            throw new IllegalArgumentException("수신자 UUID는 필수입니다.");
        }
        if (requester.getUuid().equals(receiverUuid)) {
            throw new IllegalArgumentException("자기 자신에게 채팅 요청을 보낼 수 없습니다.");
        }

        SiteUser receiver = userRepository.findByUuid(receiverUuid)
                .orElseThrow(() -> new IllegalArgumentException("수신자를 찾을 수 없습니다: " + receiverUuid));

        // 기존 채팅방이 있는지 확인 (CLOSED 상태는 제외)
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

        return chatRoomRepository.save(chatRoom);
    }

    @Transactional
    public List<Object> getMessagesWithDateNotifications(Long chatRoomId) {
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다: " + chatRoomId));
        List<ChatMessage> messages = chatMessageRepository.findByChatRoomOrderByTimestampAsc(chatRoom);
        System.out.println("Raw messages from DB: " + messages.size());

        List<Object> result = new ArrayList<>();
        DateFormat dateFormat = new SimpleDateFormat("yyyy년 MM월 dd일", Locale.KOREAN);
        String lastDate = null;

        for (ChatMessage msg : messages) {
            String currentDate = dateFormat.format(Date.from(msg.getTimestamp().atZone(ZoneId.systemDefault()).toInstant()));
            if (lastDate == null || !lastDate.equals(currentDate)) {
                result.add(new DateNotificationDTO(currentDate));
                lastDate = currentDate;
            }
            result.add(convertToChatMessageDTO(msg));
        }
        return result;
    }

    @Transactional
    public ChatRoom handleChatRequest(SiteUser currentUser, Long chatRoomId, String action) {
        System.out.println("currentUser : " + currentUser.getEmail());
        if (currentUser == null) {
            throw new IllegalArgumentException("사용자 정보가 없습니다.");
        }
        if (chatRoomId == null) {
            throw new IllegalArgumentException("채팅방 ID가 유효하지 않습니다.");
        }
        if (action == null) {
            throw new IllegalArgumentException("액션이 지정되지 않았습니다.");
        }

        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다: " + chatRoomId));

        System.out.println("getOwner : " + chatRoom.getOwner().getEmail());
        if (!chatRoom.getOwner().getEmail().equals(currentUser.getEmail())) {
            throw new SecurityException("채팅 요청을 승인, 거부, 또는 차단할 권한이 없습니다. 소유자: " + chatRoom.getOwner().getEmail());
        }

        if (!"PENDING".equals(chatRoom.getStatus())) {
            throw new IllegalStateException("현재 상태(" + chatRoom.getStatus() + ")에서는 요청을 처리할 수 없습니다. PENDING 상태에서만 가능합니다.");
        }

        String upperAction = action.toUpperCase();
        switch (upperAction) {
            case "APPROVE":
                chatRoom.setStatus("ACTIVE");
                if (!chatRoom.getParticipants().contains(currentUser)) {
                    chatRoom.getParticipants().add(currentUser);
                }
                chatMessageService.createMessage(chatRoom, currentUser,
                        currentUser.getName() + "님이 채팅을 수락하셨습니다.", MessageType.SYSTEM);
                break;
            case "REJECT":
                chatRoomRepository.delete(chatRoom);
                return null;
            case "BLOCK":
                chatRoom.setStatus("BLOCKED");
                break;
            default:
                throw new IllegalArgumentException("지원하지 않는 액션입니다: " + action);
        }

        return chatRoomRepository.save(chatRoom);
    }
    // 채팅방 나가기
    @Transactional
    public void leaveChatRoom(Long chatRoomId, String userUuid) {
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다: " + chatRoomId));

        SiteUser user = chatRoom.getParticipants().stream()
                .filter(participant -> participant.getUuid().equals(userUuid))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("사용자가 채팅방에 없습니다: " + userUuid));

        // 사용자를 채팅방에서 제거
        chatRoom.removeParticipant(user);

        // 그룹 채팅이고 참여자가 없으면 채팅방 삭제
        if ("GROUP".equals(chatRoom.getType()) && chatRoom.getParticipants().isEmpty()) {
            chatRoomRepository.delete(chatRoom);
        } else {
            // 개인 채팅이면 BLOCKED 상태로 변경
            if ("PRIVATE".equals(chatRoom.getType())) {
                chatRoom.setStatus("CLOSED");
            }
            chatRoomRepository.save(chatRoom);

            // 시스템 메시지 생성
            chatMessageService.createMessage(chatRoom, null,
                    user.getName() + "님이 채팅방을 나갔습니다.", MessageType.SYSTEM);
        }
    }
    // 개인 채팅에서 사용자 차단
    @Transactional
    public void blockUserInChat(Long chatRoomId, String blockerUuid, String blockedUuid) {
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다: " + chatRoomId));

        if (!"PRIVATE".equals(chatRoom.getType())) {
            throw new IllegalArgumentException("차단은 개인 채팅에서만 가능합니다.");
        }

        SiteUser blocker = chatRoom.getParticipants().stream()
                .filter(participant -> participant.getUuid().equals(blockerUuid))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("차단하는 사용자가 채팅방에 없습니다: " + blockerUuid));

        SiteUser blocked = chatRoom.getParticipants().stream()
                .filter(participant -> participant.getUuid().equals(blockedUuid))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("차단할 사용자가 채팅방에 없습니다: " + blockedUuid));

        // 차단 목록에 추가 (SiteUser 엔티티의 blockUser 메서드 사용)
        blocker.blockUser(blocked);

        // 채팅방을 BLOCKED 상태로 변경
        chatRoom.setStatus("BLOCKED");
        chatRoomRepository.save(chatRoom);

        // 시스템 메시지 생성
        chatMessageService.createMessage(chatRoom, null,
                blocker.getName() + "님이 " + blocked.getName() + "님을 차단했습니다.", MessageType.SYSTEM);
    }
    @Transactional(readOnly = true)
    public List<ChatRoomDTO> getChatRoomsForUser(SiteUser user) {
        List<ChatRoom> chatRooms = chatRoomRepository.findByParticipantsContainingOrPendingForUser(user);
        return chatRooms.stream().map(chat -> {
            ChatRoomDTO dto = new ChatRoomDTO();
            dto.setId(chat.getId());
            dto.setName(chat.getName());
            dto.setType(chat.getType());
            dto.setLastMessage(chat.getLastMessage());
            dto.setLastMessageTime(chat.getLastMessageTime());
            dto.setParticipants(chat.getParticipants().stream().map(p -> {
                ChatRoomDTO.SiteUserDTO userDto = new ChatRoomDTO.SiteUserDTO();
                userDto.setUuid(p.getUuid());
                userDto.setName(p.getName());
                return userDto;
            }).collect(Collectors.toList()));
            dto.setOwner(toSiteUserDTO(chat.getOwner()));
            dto.setRequester(toSiteUserDTO(chat.getRequester()));
            dto.setUnreadCount(chat.getUnreadCount());
            dto.setRequestReason(chat.getRequestReason());
            dto.setStatus(chat.getStatus());

            Optional<ChatMessage> lastMessage = chatMessageRepository.findTopByChatRoomOrderByTimestampDesc(chat);
            lastMessage.ifPresent(msg -> {
                dto.setLastMessage(msg.getContent());
                dto.setLastMessageTime(msg.getTimestamp());
            });

            List<ChatMessage> messages = chatMessageRepository.findByChatRoomOrderByTimestampAsc(chat);
            dto.setMessages(messages.stream().map(this::convertToChatMessageDTO).collect(Collectors.toList()));
            return dto;
        }).collect(Collectors.toList());
    }

    private ChatRoomDTO.SiteUserDTO toSiteUserDTO(SiteUser user) {
        if (user == null) return null;
        ChatRoomDTO.SiteUserDTO dto = new ChatRoomDTO.SiteUserDTO();
        dto.setUuid(user.getUuid());
        dto.setName(user.getName());
        return dto;
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