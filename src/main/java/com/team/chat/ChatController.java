package com.team.chat;

import com.team.user.CustomSecurityUserDetails;
import com.team.user.SiteUser;
import com.team.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.text.StringEscapeUtils;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class ChatController {

    private final ChatRoomService chatRoomService;
    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatMessageService chatMessageService;
    private final ChatRoomParticipantRepository chatRoomParticipantRepository;

    @MessageMapping("/refreshChatRooms")
    @Transactional(readOnly = true)
    public void refreshChatRooms(Principal principal, @Payload ChatRequestDTO request) {
        SiteUser currentUser = getCurrentUser(principal);
        List<ChatRoomDTO> chatRooms = chatRoomService.getChatRoomsForUser(currentUser);
        messagingTemplate.convertAndSend("/user/" + currentUser.getUuid() + "/topic/chatrooms", chatRooms);
    }
    @MessageMapping("/getMessageCount")
    @Transactional(readOnly = true)
    public void getMessageCount(Principal principal, @Payload ChatRoomDTO request) {
        SiteUser currentUser = getCurrentUser(principal);
        Long chatRoomId = request.getId();

        if (chatRoomId == null) {
            messagingTemplate.convertAndSend("/user/" + currentUser.getUuid() + "/topic/errors", "채팅방 ID가 제공되지 않았습니다.");
            return;
        }

        ChatRoom chatRoom = chatRoomService.findChatRoomById(chatRoomId);
        if (chatRoom == null || !chatRoom.getParticipants().contains(currentUser)) {
            messagingTemplate.convertAndSend("/user/" + currentUser.getUuid() + "/topic/errors", "채팅방에 접근할 권한이 없습니다.");
            return;
        }

        long messageCount = chatMessageRepository.countByChatRoom(chatRoom);
        messagingTemplate.convertAndSend("/user/" + currentUser.getUuid() + "/topic/messageCount", messageCount);
    }
    @MessageMapping("/getMessages")
    @Transactional(readOnly = true)
    public void getMessages(Principal principal, @Payload ChatRoomDTO request) {
        SiteUser currentUser = getCurrentUser(principal);
        Long chatRoomId = request.getId();
        int page = request.getPage() != null ? request.getPage() : 0; // 기본값: 0
        System.out.println(page);
        int size = request.getSize() != null ? Math.min(request.getSize(), 50) : 50; // 최대 50개로 제한

        if (chatRoomId == null) {
            messagingTemplate.convertAndSend("/user/" + currentUser.getUuid() + "/topic/errors", "채팅방 ID가 제공되지 않았습니다.");
            return;
        }

        ChatRoom chatRoom = chatRoomService.findChatRoomById(chatRoomId);
        if (chatRoom == null || !chatRoom.getParticipants().contains(currentUser)) {
            messagingTemplate.convertAndSend("/user/" + currentUser.getUuid() + "/topic/errors", "채팅방에 접근할 권한이 없습니다.");
            return;
        }

        List<ChatRoomDTO.ChatMessageDTO> messages = chatRoomService.getMessages(chatRoom, page, size);
        messagingTemplate.convertAndSend("/user/" + currentUser.getUuid() + "/topic/messages", messages);
    }

    @MessageMapping("/handleChatRequest")
    @Transactional
    public void handleChatRequest(Principal principal, @Payload ChatRequestDTO request) {
        SiteUser currentUser = getCurrentUser(principal);
        chatRoomService.handleChatRequest(currentUser, request.getChatRoomId(), request.getAction());
    }

    @MessageMapping("/blockUser")
    @Transactional
    public void blockUser(Principal principal, @Payload ChatRequestDTO request) {
        SiteUser currentUser = getCurrentUser(principal);
        ChatRoom chatRoom = chatRoomService.findChatRoomById(request.getChatRoomId());
        String blockedUuid = chatRoom.getParticipants().stream()
                .filter(p -> !p.getUuid().equals(currentUser.getUuid()))
                .findFirst()
                .map(SiteUser::getUuid)
                .orElseGet(() -> {
                    if (chatRoom.getRequester().getUuid().equals(currentUser.getUuid())) {
                        return chatRoom.getOwner().getUuid();
                    } else if (chatRoom.getOwner().getUuid().equals(currentUser.getUuid())) {
                        return chatRoom.getRequester().getUuid();
                    }
                    return null;
                });
        if (blockedUuid == null) {
            messagingTemplate.convertAndSend("/user/" + currentUser.getUuid() + "/topic/errors", "차단할 상대방을 찾을 수 없습니다.");
            return;
        }
        chatRoomService.blockUserInChat(request.getChatRoomId(), currentUser.getUuid(), blockedUuid);
    }

    @MessageMapping("/leaveChatRoom")
    @Transactional
    public void leaveChatRoom(Principal principal, @Payload ChatRequestDTO request) {
        SiteUser currentUser = getCurrentUser(principal);
        chatRoomService.leaveChatRoom(request.getChatRoomId(), currentUser.getUuid());
    }

    @MessageMapping("/sendMessage")
    @Transactional
    public void sendMessage(@Payload ChatRoomDTO.ChatMessageDTO messageDTO, Principal principal) {
        SiteUser sender = getCurrentUser(principal);
        String content = StringEscapeUtils.escapeHtml4(messageDTO.getContent());
        if (content.length() > 1000) {
            messagingTemplate.convertAndSend("/user/" + sender.getUuid() + "/topic/errors", "메시지가 너무 깁니다. 최대 1000자.");
            return;
        }

        ChatRoom chatRoom = chatRoomService.findChatRoomById(messageDTO.getChatRoomId());
        if ("CLOSED".equals(chatRoom.getStatus()) || "BLOCKED".equals(chatRoom.getStatus())) {
            messagingTemplate.convertAndSend("/user/" + sender.getUuid() + "/topic/errors", "이 채팅방은 메시지를 보낼 수 없습니다.");
            return;
        }

        ChatMessage message = chatMessageService.createMessage(chatRoom, sender, content, MessageType.NORMAL);
        chatRoom.setLastMessage(message.getContent());
        chatRoom.setLastMessageTime(message.getTimestamp());
        chatRoomRepository.save(chatRoom);

        ChatRoomDTO.ChatMessageDTO messageDto = chatRoomService.convertToChatMessageDTO(message);
        chatRoom.getParticipants().forEach(p ->
                messagingTemplate.convertAndSend("/user/" + p.getUuid() + "/topic/messages", messageDto));

        sendPushNotification(chatRoom, message, sender);
    }

    @MessageMapping("/toggleNotification")
    @Transactional
    public void toggleNotification(Principal principal, @Payload ChatRequestDTO request) {
        SiteUser currentUser = getCurrentUser(principal);
        ChatRoom chatRoom = chatRoomService.findChatRoomById(request.getChatRoomId());
        if (!chatRoom.getParticipants().contains(currentUser)) {
            messagingTemplate.convertAndSend("/user/" + currentUser.getUuid() + "/topic/errors", "권한이 없습니다.");
            return;
        }
        ChatRoomParticipant participant = chatRoomParticipantRepository.findByChatRoomAndUser(chatRoom, currentUser)
                .orElseThrow(() -> new IllegalStateException("Participant not found"));
        boolean newState = "ON".equals(request.getAction());
        participant.setNotificationEnabled(newState);
        chatRoomParticipantRepository.save(participant);

        Map<String, Object> response = new HashMap<>();
        response.put("chatRoomId", chatRoom.getId());
        response.put("notificationEnabled", newState);
        messagingTemplate.convertAndSend("/user/" + currentUser.getUuid() + "/topic/notificationUpdate", response);
    }

    @MessageMapping("/markMessagesAsRead")
    @Transactional
    public void markMessagesAsRead(Principal principal, @Payload ChatRequestDTO request) {
        SiteUser currentUser = getCurrentUser(principal);
        ChatRoom chatRoom = chatRoomService.findChatRoomById(request.getChatRoomId());
        int unreadCount = chatMessageService.markMessagesAsRead(chatRoom, currentUser);
        Map<String, Object> update = new HashMap<>();
        update.put("chatRoomId", chatRoom.getId());
        update.put("unreadCount", unreadCount);
        messagingTemplate.convertAndSend("/user/" + currentUser.getUuid() + "/topic/readUpdate", update);
    }

    private SiteUser getCurrentUser(Principal principal) {
        Authentication auth = (Authentication) principal;
        CustomSecurityUserDetails userDetails = (CustomSecurityUserDetails) auth.getPrincipal();
        return userRepository.findByUuidWithBlockedUsers(userDetails.getSiteUser().getUuid())
                .orElseThrow(() -> new SecurityException("사용자를 찾을 수 없습니다: " + userDetails.getSiteUser().getUuid()));
    }

    private void sendPushNotification(ChatRoom chatRoom, ChatMessage message, SiteUser sender) {
        PushNotificationDTO notification = new PushNotificationDTO(
                chatRoom.getId(),
                sender.getName(),
                message.getContent(),
                message.getTimestamp().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        );
        chatRoom.getParticipantSettings().stream()
                .filter(p -> !p.getUser().getUuid().equals(sender.getUuid()))
                .filter(ChatRoomParticipant::isNotificationEnabled)
                .filter(p -> !message.getReadBy().contains(p.getUser()))
                .forEach(p -> messagingTemplate.convertAndSend("/user/" + p.getUser().getUuid() + "/topic/notifications", notification));
    }

    @MessageExceptionHandler(IllegalArgumentException.class)
    public void handleIllegalArgument(Principal principal, IllegalArgumentException e) {
        messagingTemplate.convertAndSend("/user/" + principal.getName() + "/topic/errors", "잘못된 요청: " + e.getMessage());
    }
}