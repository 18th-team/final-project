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

import java.util.List;

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

        SiteUser requester = chatRoom.getRequester();
        SiteUser owner = chatRoom.getOwner();

        messagingTemplate.convertAndSend("/user/" + requester.getUuid() + "/topic/chatrooms",
                chatRoomService.getChatRoomsForUser(requester));
        messagingTemplate.convertAndSend("/user/" + owner.getUuid() + "/topic/chatrooms",
                chatRoomService.getChatRoomsForUser(owner));
    }

    @MessageMapping("/refreshChatRooms")
    public void refreshChatRooms(@Payload RefreshChatRoomsDTO payload) {
        String uuid = payload.getUuid();
        System.out.println("Refreshing chat rooms for: " + uuid);
        SiteUser user = userRepository.findByUuid(uuid)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + uuid));
        List<ChatRoomDTO> chatRooms = chatRoomService.getChatRoomsForUser(user);
        System.out.println("Sending chat rooms: " + chatRooms.size() + " to /user/" + uuid + "/topic/chatrooms");
        messagingTemplate.convertAndSend("/user/" + uuid + "/topic/chatrooms", chatRooms);
    }

    @MessageExceptionHandler
    public void handleException(Exception e) {
        messagingTemplate.convertAndSendToUser("system", "/topic/errors", e.getMessage());
    }
}