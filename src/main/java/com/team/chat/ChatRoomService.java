package com.team.chat;

import com.team.user.CustomSecurityUserDetails;
import com.team.user.SiteUser;
import com.team.user.UserRepository;
import lombok.RequiredArgsConstructor;
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
    public ChatRoomDTO requestPersonalChat(CustomSecurityUserDetails userDetails, String receiverEmail, String reason) {
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
                .requesterEmail(requester.getEmail()) // 수정
                .receiverEmail(receiver.getEmail())
                .participants(List.of(requester))
                .requestReason(reason)
                .status("PENDING")
                .lastMessageTime(LocalDateTime.now())
                .build();

        chatRoom = chatRoomRepository.save(chatRoom);
        return toDTO(chatRoom, requester);
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
    public List<ChatRoomDTO> getChatRoomsForUser(SiteUser user) {
      /*  List<ChatRoom> chatRooms = chatRoomRepository.findByParticipantsContainingOrPendingForUser(user, user.getEmail());
        return chatRooms.stream().map(chatRoom -> toDTO(chatRoom, user)).collect(Collectors.toList());*/
        List<ChatRoom> chatRooms = chatRoomRepository.findByParticipantsContainingOrPendingForUser(user, user.getEmail());
        System.out.println("Chat rooms for user " + user.getEmail() + ": " + chatRooms.size()); // 디버깅 로그
        chatRooms.forEach(chat -> System.out.println("Chat: " + chat.getId() + ", Status: " + chat.getStatus() + ", Type: " + chat.getType()));
        return chatRooms.stream().map(chatRoom -> toDTO(chatRoom, user)).collect(Collectors.toList());
    }

    private ChatRoomDTO toDTO(ChatRoom chatRoom, SiteUser currentUser) {
        String displayName = chatRoom.getName();
        if ("PERSONAL".equals(chatRoom.getType())) {
            String currentUserEmail = currentUser.getEmail();
            if (currentUserEmail.equals(chatRoom.getRequesterEmail())) {
                displayName = userRepository.findByEmail(chatRoom.getReceiverEmail())
                        .map(SiteUser::getName)
                        .orElse(chatRoom.getName());
            } else if (currentUserEmail.equals(chatRoom.getReceiverEmail())) {
                displayName = userRepository.findByEmail(chatRoom.getRequesterEmail())
                        .map(SiteUser::getName)
                        .orElse(chatRoom.getName());
            }
        }
        return new ChatRoomDTO(
                chatRoom.getId(),
                displayName,
                chatRoom.getType(),
                chatRoom.getLastMessage(),
                chatRoom.getLastMessageTime(),
                chatRoom.getParticipants().stream().map(SiteUser::getName).collect(Collectors.toList()),
                chatRoom.getOwner() != null ? chatRoom.getOwner().getName() : null,
                chatRoom.getUnreadCount(),
                chatRoom.getRequestReason(),
                chatRoom.getStatus(),
                chatRoom.getRequesterEmail()
        );
    }
}