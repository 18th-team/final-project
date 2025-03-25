package com.team.chat;

import com.team.user.CustomSecurityUserDetails;
import com.team.user.SiteUser;
import com.team.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

@Controller
@RequiredArgsConstructor
public class ChatController {

    private final ChatRoomService chatRoomService;
    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepository;

    @MessageMapping("/handleChatRequest")
    @Transactional
    public void handleChatRequest(@AuthenticationPrincipal CustomSecurityUserDetails userDetails,
                                  @Payload ChatRequestDTO request) {
        if (userDetails == null) {
            throw new SecurityException("인증되지 않은 사용자입니다.");
        }
        SiteUser currentUser = userDetails.getSiteUser();

        ChatRoom chatRoom = chatRoomService.handleChatRequest(currentUser, request.getChatRoomId(), request.getAction());

        SiteUser requester = userRepository.findByEmail(chatRoom.getRequesterEmail())
                .orElseThrow(() -> new IllegalArgumentException("요청자를 찾을 수 없습니다: " + chatRoom.getRequesterEmail()));

        messagingTemplate.convertAndSend("/user/" + chatRoom.getRequesterEmail() + "/topic/chatrooms",
                chatRoomService.getChatRoomsForUser(requester));
        messagingTemplate.convertAndSend("/user/" + chatRoom.getReceiverEmail() + "/topic/chatrooms",
                chatRoomService.getChatRoomsForUser(currentUser));
    }

    @MessageMapping("/refreshChatRooms")
    public void refreshChatRooms(@Payload RefreshChatRoomsDTO payload) {
        String email = payload.getEmail();
        System.out.println("Refreshing chat rooms for: " + email);
        SiteUser user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + email));
        messagingTemplate.convertAndSend("/user/" + email + "/topic/chatrooms",
                chatRoomService.getChatRoomsForUser(user));
    }

    @MessageExceptionHandler
    public void handleException(Exception e) {
        messagingTemplate.convertAndSendToUser("system", "/topic/errors", e.getMessage());
    }
}

class RefreshChatRoomsDTO {
    private String email;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}