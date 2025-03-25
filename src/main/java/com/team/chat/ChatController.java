package com.team.chat;

import com.team.user.CustomSecurityUserDetails;
import com.team.user.SiteUser;
import com.team.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class ChatController {

    private final ChatRoomService chatRoomService;
    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepository;

    @MessageMapping("/refreshChatRooms")
    public void refreshChatRooms(@Payload Map<String, String> payload,
                                 @AuthenticationPrincipal CustomSecurityUserDetails userDetails) {
        String email = payload.get("email");
        if (userDetails == null || !userDetails.getUsername().equals(email)) {
            throw new AccessDeniedException("Invalid user");
        }
        SiteUser user = userDetails.getSiteUser();
        List<ChatRoom> chatRooms = chatRoomService.getChatRoomsForUser(user);
        messagingTemplate.convertAndSend("/user/" + email + "/topic/chatrooms", chatRooms);
    }

    @MessageMapping("/handleChatRequest")
    public void handleChatRequest(@AuthenticationPrincipal CustomSecurityUserDetails userDetails,
                                  @Payload ChatRequestDTO request) {
        if (userDetails == null) {
            throw new SecurityException("인증되지 않은 사용자입니다.");
        }
        SiteUser currentUser = userDetails.getSiteUser();
        ChatRoom chatRoom = chatRoomService.handleChatRequest(currentUser, request.getChatRoomId(), request.getAction());

        SiteUser requester = userRepository.findByEmail(chatRoom.getRequesterEmail())
                .orElseThrow(() -> new IllegalArgumentException("요청자를 찾을 수 없습니다: " + chatRoom.getRequesterEmail()));

        List<ChatRoom> requesterChatRooms = chatRoomService.getChatRoomsForUser(requester);
        List<ChatRoom> currentUserChatRooms = chatRoomService.getChatRoomsForUser(currentUser);

        messagingTemplate.convertAndSend("/user/" + chatRoom.getRequesterEmail() + "/topic/chatrooms", requesterChatRooms);
        messagingTemplate.convertAndSend("/user/" + chatRoom.getReceiverEmail() + "/topic/chatrooms", currentUserChatRooms);
    }
}