package com.team.chat;

import com.team.user.CustomSecurityUserDetails;
import com.team.user.SiteUser;
import com.team.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;
    private final UserRepository userRepository;
    private final ChatMessageService chatMessageService;
    private final ChatMessageRepository chatMessageRepository;

    @Transactional
    public ChatRoom requestPersonalChat(CustomSecurityUserDetails userDetails, String receiverUuid, String reason) { // email -> uuid
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

        if (chatRoomRepository.existsByRequesterAndOwnerAndType(requester, receiver, "PRIVATE")) {
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
    public ChatRoom handleChatRequest(SiteUser currentUser, Long chatRoomId, String action) {
        System.out.println("currentUser : " + currentUser.getEmail());
        // 입력값 검증
        if (currentUser == null) {
            throw new IllegalArgumentException("사용자 정보가 없습니다.");
        }
        if (chatRoomId == null) {
            throw new IllegalArgumentException("채팅방 ID가 유효하지 않습니다.");
        }
        if (action == null) {
            throw new IllegalArgumentException("액션이 지정되지 않았습니다.");
        }

        // 채팅방 조회
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다: " + chatRoomId));

        // 권한 확인
        System.out.println("getOwner : " + chatRoom.getOwner().getEmail());
        if (!chatRoom.getOwner().getEmail().equals(currentUser.getEmail())) {
            throw new SecurityException("채팅 요청을 승인, 거부, 또는 차단할 권한이 없습니다. 소유자: " + chatRoom.getOwner().getEmail());
        }

        // 상태 검증
        if (!"PENDING".equals(chatRoom.getStatus())) {
            throw new IllegalStateException("현재 상태(" + chatRoom.getStatus() + ")에서는 요청을 처리할 수 없습니다. PENDING 상태에서만 가능합니다.");
        }

        // 액션 처리
        String upperAction = action.toUpperCase();
        switch (upperAction) {
            case "APPROVE":
                chatRoom.setStatus("ACTIVE");
                if (!chatRoom.getParticipants().contains(currentUser)) {
                    chatRoom.getParticipants().add(currentUser); //수신자 추가
                }
                // 수락 메시지 생성
                chatMessageService.createMessage(chatRoom, currentUser,
                        currentUser.getName() + "님이 채팅을 수락하셨습니다.", MessageType.SYSTEM);
                break;
            case "REJECT":
                chatRoomRepository.delete(chatRoom);
                return null; // 삭제 후 null 반환 (클라이언트에서 제외되도록)
            case "BLOCK":
                chatRoom.setStatus("BLOCKED");
                break;
            default:
                throw new IllegalArgumentException("지원하지 않는 액션입니다: " + action);
        }

        // 변경사항 저장
        return chatRoomRepository.save(chatRoom);
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

            // 마지막 메시지 설정
            Optional<ChatMessage> lastMessage = chatMessageRepository.findTopByChatRoomOrderByTimestampDesc(chat);
            lastMessage.ifPresent(msg -> {
                dto.setLastMessage(msg.getContent());
                dto.setLastMessageTime(msg.getTimestamp());
            });

            // 메시지 목록 추가 (필요 시 제한)
            List<ChatMessage> messages = chatMessageRepository.findByChatRoomOrderByTimestampAsc(chat);
            dto.setMessages(messages.stream().map(msg -> {
                ChatRoomDTO.ChatMessageDTO msgDto = new ChatRoomDTO.ChatMessageDTO();
                msgDto.setId(msg.getId());
                msgDto.setChatRoomId(chat.getId());
                msgDto.setSender(new ChatRoomDTO.SiteUserDTO(msg.getSender().getUuid(), msg.getSender().getName()));
                msgDto.setContent(msg.getContent());
                msgDto.setTimestamp(msg.getTimestamp().toInstant(ZoneId.systemDefault().getRules().getOffset(msg.getTimestamp())).toEpochMilli());
                msgDto.setType(msg.getType().name());
                return msgDto;
            }).collect(Collectors.toList()));
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
}