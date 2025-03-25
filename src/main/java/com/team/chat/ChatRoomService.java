package com.team.chat;

import com.team.user.CustomSecurityUserDetails;
import com.team.user.SiteUser;
import com.team.user.UserRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatRoomService(ChatRoomRepository chatRoomRepository, UserRepository userRepository, SimpMessagingTemplate messagingTemplate) {
        this.chatRoomRepository = chatRoomRepository;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
    }


    public ChatRoomDTO requestPersonalChat(CustomSecurityUserDetails userDetails, String targetEmail, String reason) {
        SiteUser requester = userDetails.getSiteUser();
        SiteUser targetUser = userRepository.findByEmail(targetEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found with email: " + targetEmail));

        ChatRoom chatRoom = ChatRoom.builder()
                .name(requester.getName())
                .type("PERSONAL")
                .lastMessage(null)
                .lastMessageTime(LocalDateTime.now())
                .participants(List.of(requester, targetUser))
                .owner(null)
                .unreadCount(0)
                .requestReason(reason)
                .status("PENDING")
                .build();
        chatRoom = chatRoomRepository.save(chatRoom);
        // 요청자와 수신자 모두에게 최신 데이터 전송
        List<ChatRoomDTO> requesterChatRooms = getChatRoomsForUser(requester);
        System.out.println("Sending to requester: /user/" + requester.getEmail() + "/topic/chatrooms - " + requesterChatRooms);
        messagingTemplate.convertAndSend("/user/" + requester.getEmail() + "/topic/chatrooms", requesterChatRooms);

        List<ChatRoomDTO> receiverChatRooms = getChatRoomsForUser(targetUser);
        System.out.println("Sending to receiver: /user/" + targetUser.getEmail() + "/topic/chatrooms - " + receiverChatRooms);
        messagingTemplate.convertAndSend("/user/" + targetUser.getEmail() + "/topic/chatrooms", receiverChatRooms);

        return toDTO(chatRoom);
    }
    // 사용자의 채팅방 목록 가져오기
    @Transactional(readOnly = true)
    public List<ChatRoomDTO> getChatRoomsForUser(SiteUser user) {
        List<ChatRoom> chatRooms = chatRoomRepository.findByParticipantsContaining(user);
        System.out.println("Fetched chat rooms for " + user.getEmail() + ": " + chatRooms.size());
        return chatRooms.stream().map(this::toDTO).collect(Collectors.toList());
    }


    public void createGroupChat(SiteUser creator, String groupName) {
        ChatRoom chatRoom = ChatRoom.builder()
                .name(groupName)
                .type("GROUP")
                .lastMessage(null)
                .lastMessageTime(LocalDateTime.now())
                .participants(List.of(creator))
                .owner(creator)
                .unreadCount(0)
                .requestReason(null)
                .status("APPROVED")
                .build();

        chatRoomRepository.save(chatRoom);
        notifyUser(creator, "그룹 채팅이 생성되었습니다.");
    }

    @Transactional
    public void handleChatRequest(Long chatRoomId, String action) {
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId).orElseThrow();
        switch (action) {
            case "APPROVE":
                chatRoom.setStatus("APPROVED");
                break;
            case "REJECT":
                chatRoom.setStatus("REJECTED");
                break;
            case "BLOCK":
                chatRoom.setStatus("BLOCKED");
                break;
        }
        chatRoomRepository.save(chatRoom);
    }

    @Transactional
    public void requestGroupJoin(SiteUser user, Long groupId, String reason) {
        ChatRoom chatRoom = chatRoomRepository.findById(groupId).orElseThrow();
        chatRoom.setRequestReason(reason);
        chatRoom.setStatus("PENDING");
        chatRoomRepository.save(chatRoom);
    }
    @Transactional(readOnly = true)
    public ChatRoom getChatRoomById(Long chatRoomId) {
        return chatRoomRepository.findById(chatRoomId).orElseThrow();
    }

    private void notifyUser(SiteUser user, String message) {
        messagingTemplate.convertAndSend("/user/" + user.getName() + "/topic/notification", message);
    }

    private void notifyParticipants(ChatRoom chatRoom, String message) {
        chatRoom.getParticipants().forEach(user ->
                messagingTemplate.convertAndSend("/user/" + user.getName() + "/topic/notification", message));
    }

    private ChatRoomDTO toDTO(ChatRoom chatRoom) {
        return new ChatRoomDTO(
                chatRoom.getId(),
                chatRoom.getName(),
                chatRoom.getType(),
                chatRoom.getLastMessage(),
                chatRoom.getLastMessageTime(),
                chatRoom.getParticipants().stream().map(SiteUser::getName).collect(Collectors.toList()),
                chatRoom.getOwner() != null ? chatRoom.getOwner().getName() : null,
                chatRoom.getUnreadCount(),
                chatRoom.getRequestReason(),
                chatRoom.getStatus()
        );
    }
}