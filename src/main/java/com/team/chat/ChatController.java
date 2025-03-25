package com.team.chat;

import com.team.user.CustomSecurityUserDetails;
import lombok.Data;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.List;

@Controller
public class ChatController {

    private final ChatRoomService chatRoomService;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatController(ChatRoomService chatRoomService, SimpMessagingTemplate messagingTemplate) {
        this.chatRoomService = chatRoomService;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/refreshChatRooms")
    public void refreshChatRooms(SimpMessageHeaderAccessor headerAccessor) {
        Principal principal = headerAccessor.getUser();
        if (principal instanceof Authentication auth && auth.isAuthenticated() && auth.getPrincipal() instanceof CustomSecurityUserDetails userDetails) {
            System.out.println("Refreshing chat rooms for user: " + principal.getName());
            List<ChatRoomDTO> chatRooms = chatRoomService.getChatRoomsForUser(userDetails.getSiteUser());
            String destination = "/user/" + principal.getName() + "/topic/chatrooms";
            System.out.println("Sending chat rooms to " + destination + ": " + chatRooms);
            messagingTemplate.convertAndSend(destination, chatRooms);
        } else {
            System.out.println("No authenticated user found for /refreshChatRooms, Principal: " + (principal != null ? principal.getName() : "null"));
        }
    }

    @MessageMapping("/handleChatRequest")
    public void handleChatRequest(ChatRequest chatRequest, SimpMessageHeaderAccessor headerAccessor) {
        Principal principal = headerAccessor.getUser();
        if (principal instanceof Authentication auth && auth.isAuthenticated() && auth.getPrincipal() instanceof CustomSecurityUserDetails userDetails && chatRequest != null) {
            chatRoomService.handleChatRequest(chatRequest.getChatRoomId(), chatRequest.getAction());
            List<ChatRoomDTO> updatedChatRooms = chatRoomService.getChatRoomsForUser(userDetails.getSiteUser());
            String destination = "/user/" + principal.getName() + "/topic/chatrooms";
            messagingTemplate.convertAndSend(destination, updatedChatRooms);
            // 상대방에게도 업데이트 전송 (필요 시)
            ChatRoom chatRoom = chatRoomService.getChatRoomById(chatRequest.getChatRoomId());
            chatRoom.getParticipants().forEach(participant -> {
                if (!participant.getEmail().equals(principal.getName())) {
                    List<ChatRoomDTO> participantChatRooms = chatRoomService.getChatRoomsForUser(participant);
                    messagingTemplate.convertAndSend("/user/" + participant.getEmail() + "/topic/chatrooms", participantChatRooms);
                }
            });
        }
    }

    @MessageMapping("/requestGroupJoin")
    public void requestGroupJoin(GroupJoinRequest request, SimpMessageHeaderAccessor headerAccessor) {
        Principal principal = headerAccessor.getUser();
        if (principal instanceof Authentication auth && auth.isAuthenticated() && auth.getPrincipal() instanceof CustomSecurityUserDetails userDetails && request != null) {
            chatRoomService.requestGroupJoin(userDetails.getSiteUser(), request.getGroupId(), request.getReason());
            List<ChatRoomDTO> updatedChatRooms = chatRoomService.getChatRoomsForUser(userDetails.getSiteUser());
            String destination = "/user/" + principal.getName() + "/topic/chatrooms";
            messagingTemplate.convertAndSend(destination, updatedChatRooms);
            // 그룹 소유자에게도 업데이트 전송
            ChatRoom chatRoom = chatRoomService.getChatRoomById(request.getGroupId());
            if (chatRoom.getOwner() != null && !chatRoom.getOwner().getEmail().equals(principal.getName())) {
                List<ChatRoomDTO> ownerChatRooms = chatRoomService.getChatRoomsForUser(chatRoom.getOwner());
                messagingTemplate.convertAndSend("/user/" + chatRoom.getOwner().getEmail() + "/topic/chatrooms", ownerChatRooms);
            }
        }
    }

    private ChatRoom getChatRoomById(Long chatRoomId) {
        return chatRoomService.getChatRoomById(chatRoomId);
    }
}
@Data
class ChatRequest {
    private Long chatRoomId;
    private String action;

    public Long getChatRoomId() { return chatRoomId; }
    public String getAction() { return action; }
}

@Data
class GroupJoinRequest {
    private Long groupId;
    private String reason;

    public Long getGroupId() { return groupId; }
    public String getReason() { return reason; }
}