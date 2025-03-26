package com.team.chat;

import com.team.user.CustomSecurityUserDetails;
import com.team.user.SiteUser;
import com.team.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;
    private final UserRepository userRepository;

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
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다: " + chatRoomId));

        if (!chatRoom.getOwner().equals(currentUser)) {
            throw new SecurityException("요청을 승인, 거부, 차단할 권한이 없습니다.");
        }

        switch (action.toUpperCase()) {
            case "APPROVE":
                chatRoom.setStatus("ACTIVE");
                chatRoom.getParticipants().add(currentUser); // 수신자도 참여자로 추가
                break;
            case "REJECT":
                chatRoom.setStatus("REJECTED");
                break;
            case "BLOCK":
                chatRoom.setStatus("BLOCKED");
                break;
            default:
                throw new IllegalArgumentException("잘못된 작업입니다: " + action);
        }
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