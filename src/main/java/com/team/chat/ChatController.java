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
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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

    // UUID와 세션 ID를 매핑하여 온라인 상태 관리
    private final Map<String, Set<String>> userSessions = new ConcurrentHashMap<>();

    @EventListener
    public void handleWebSocketConnect(SessionConnectEvent event) {
        Principal principal = event.getUser();
        if (principal != null) {
            SiteUser user = getCurrentUser(principal);
            String uuid = user.getUuid();
            String sessionId = event.getMessage().getHeaders().get("simpSessionId").toString();
            userSessions.computeIfAbsent(uuid, k -> new HashSet<>()).add(sessionId);
            System.out.println("User connected: " + uuid + ", Session ID: " + sessionId);
            broadcastOnlineStatus(uuid, true);
            userRepository.save(user);
        }
    }

    @EventListener
    public void handleWebSocketDisconnect(SessionDisconnectEvent event) {
        Principal principal = event.getUser();
        if (principal != null) {
            SiteUser user = getCurrentUser(principal);
            String uuid = user.getUuid();
            String sessionId = event.getSessionId();
            Set<String> sessions = userSessions.getOrDefault(uuid, new HashSet<>());
            sessions.remove(sessionId);
            System.out.println("Session removed: " + sessionId + ", Remaining sessions: " + sessions.size() + " for user: " + uuid);

            if (sessions.isEmpty()) {
                userSessions.remove(uuid);
                System.out.println("All sessions closed, broadcasting offline status for user: " + uuid);
                user.setLastOnline(LocalDateTime.now());
                userRepository.save(user);
                broadcastOnlineStatus(uuid, false);

            } else {
                System.out.println("User " + uuid + " still has active sessions: " + sessions);
            }
        }
    }

    private void broadcastOnlineStatus(String uuid, boolean isOnline) {
        List<ChatRoom> chatRooms = chatRoomRepository.findRoomsAndFetchParticipantsByUserUuid(uuid);
        if (chatRooms == null || chatRooms.isEmpty()) {
            System.out.println("No chat rooms found for user: " + uuid + ", cannot broadcast status");
            return;
        }
        Optional<SiteUser> GetUser =  userRepository.findByUuid(uuid);
        if (GetUser.isPresent()) {
            SiteUser User = GetUser.get();
            String email = User.getEmail();
            System.out.println("Broadcasting status for user: " + uuid + ", email: " + email  + ", isOnline: " + isOnline + ", to " + chatRooms.size() + " chat rooms");
        }

        for (ChatRoom chatRoom : chatRooms) {
            chatRoom.getParticipants().stream()
                    .filter(p -> !p.getUuid().equals(uuid)) // 자신 제외
                    .forEach(targetUser -> {
                        System.out.println("Chat room ID: " + chatRoom.getId() + ", Participants: " + targetUser.getEmail());
                        Map<String, Object> status = new HashMap<>();
                        status.put("uuid", uuid);
                        status.put("isOnline", isOnline);
                        if (!isOnline) {
                            SiteUser user = userRepository.findByUuid(uuid).orElse(null);
                            if (user != null && user.getLastOnline() != null) {
                                Long lastTime = user.getLastOnline().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                                status.put("lastOnline", lastTime);
                            }
                        }
                        String destination = "/user/" + targetUser.getUuid() + "/topic/onlineStatus";
                        System.out.println("Sending to " + destination + ": " + status);
                        messagingTemplate.convertAndSend(destination, status);
                    });
        }
    }
    @MessageMapping("/refreshChatRooms")
    @Transactional(readOnly = true)
    public void refreshChatRooms(Principal principal, @Payload ChatRequestDTO request) {
        SiteUser currentUser = getCurrentUser(principal);
        List<ChatRoomDTO> chatRooms = chatRoomService.getChatRoomsForUser(currentUser);
        messagingTemplate.convertAndSend("/user/" + currentUser.getUuid() + "/topic/chatrooms", chatRooms);
    }
    // 온라인 여부 확인, 오프라인일 경우 마지막 접속 시간 반환
    @MessageMapping("/onlineStatus")
    @Transactional(readOnly = true)
    public void OnlineStatus(Principal principal, @Payload ChatRequestDTO request) {
        SiteUser currentUser = getCurrentUser(principal);
        if (request == null || request.getChatRoomId() == null) {
            messagingTemplate.convertAndSend("/user/" + currentUser.getUuid() + "/topic/errors", "채팅방 ID가 제공되지 않았습니다.");
            return;
        }


        ChatRoom chatRoom = chatRoomService.findChatRoomById(request.getChatRoomId());
        if (chatRoom == null) {
            messagingTemplate.convertAndSend("/user/" + currentUser.getUuid() + "/topic/errors", "채팅방을 찾을 수 없습니다.");
            return;
        }
        if (!chatRoom.getParticipants().stream().anyMatch(p -> p.getUuid().equals(currentUser.getUuid()))) {
            messagingTemplate.convertAndSend("/user/" + currentUser.getUuid() + "/topic/errors", "이 채팅방에 접근할 권한이 없습니다.");
            return;
        }
        // 상대방 유저 찾기 (1:1 채팅이므로 현재 유저를 제외한 나머지 한 명)
        SiteUser targetUser = chatRoom.getParticipants().stream()
                .filter(p -> !p.getUuid().equals(currentUser.getUuid()))
                .findFirst()
                .orElse(null);

        if (targetUser == null) {
            messagingTemplate.convertAndSend("/user/" + currentUser.getUuid() + "/topic/errors", "대상 사용자를 찾을 수 없습니다.");
            return;
        }

        // 상대방 유저의 온라인 상태 반환
        String targetUuid = targetUser.getUuid();
        boolean isOnline = userSessions.containsKey(targetUuid);
        if (targetUuid == null) {
            messagingTemplate.convertAndSend("/user/" + currentUser.getUuid() + "/topic/errors", "대상 사용자를 찾을 수 없습니다.");
            return;
        }
        Map<String, Object> response = new HashMap<>();
        response.put("uuid", targetUuid);
        response.put("isOnline", isOnline);
        if (!isOnline) {
            Long lastTime = targetUser.getLastOnline() != null
                    ? targetUser.getLastOnline().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    : null; // null일 경우 프론트에서 "접속 기록 없음" 처리
            response.put("lastOnline", lastTime);
        }
        System.out.println("Sending online status to " + currentUser.getUuid() + ": " + response);
        messagingTemplate.convertAndSend("/user/" + currentUser.getUuid() + "/topic/onlineStatus", response);
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