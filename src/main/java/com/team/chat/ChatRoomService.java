package com.team.chat;

import com.team.user.CustomSecurityUserDetails;
import com.team.user.SiteUser;
import com.team.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;
    private final UserRepository userRepository;

    @Transactional
    public ChatRoom requestPersonalChat(CustomSecurityUserDetails userDetails, String receiverEmail, String reason) {
        if (userDetails == null) {
            throw new IllegalArgumentException("인증된 사용자가 필요합니다.");
        }
        SiteUser requester = userDetails.getSiteUser();
        if (receiverEmail == null || receiverEmail.trim().isEmpty()) {
            throw new IllegalArgumentException("수신자 이메일은 필수입니다.");
        }
        if (requester.getEmail().equals(receiverEmail)) {
            throw new IllegalArgumentException("자기 자신에게 채팅 요청을 보낼 수 없습니다.");
        }

        SiteUser receiver = userRepository.findByEmail(receiverEmail)
                .orElseThrow(() -> new IllegalArgumentException("수신자를 찾을 수 없습니다: " + receiverEmail));

        if (chatRoomRepository.existsByRequesterEmailAndReceiverEmailAndType(requester.getEmail(), receiverEmail, "PERSONAL")) {
            throw new IllegalStateException("이미 존재하는 개인 채팅 요청입니다.");
        }

        ChatRoom chatRoom = ChatRoom.builder()
                .type("PERSONAL")
                .name(receiver.getName())
                .requesterEmail(requester.getEmail())
                .receiverEmail(receiver.getEmail())
                .participants(List.of(requester))
                .requestReason(reason)
                .status("PENDING")
                .lastMessageTime(LocalDateTime.now())
                .build();

        return chatRoomRepository.save(chatRoom);
    }

    @Transactional
    public ChatRoom handleChatRequest(SiteUser currentUser, Long chatRoomId, String action) {
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다: " + chatRoomId));

        if (!chatRoom.getReceiverEmail().equals(currentUser.getEmail())) {
            throw new SecurityException("요청을 승인, 거부, 차단할 권한이 없습니다.");
        }

        switch (action.toUpperCase()) {
            case "APPROVE":
                chatRoom.setStatus("APPROVED");
                chatRoom.getParticipants().add(currentUser);
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
    public List<ChatRoom> getChatRoomsForUser(SiteUser user) {
        List<ChatRoom> chatRooms = chatRoomRepository.findByParticipantsContainingOrPendingForUser(user, user.getEmail());
        System.out.println("Chat rooms for user " + user.getEmail() + ": " + chatRooms.size()); // 디버깅 로그
        chatRooms.forEach(chat -> System.out.println("Chat: " + chat.getId() + ", Status: " + chat.getStatus() + ", Type: " + chat.getType()));
        return chatRooms;
    }
}