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

            // 현재 사용자 상태 전송
            Map<String, Object> selfStatus = new HashMap<>();
            selfStatus.put("uuid", uuid);
            selfStatus.put("isOnline", true);
            messagingTemplate.convertAndSend("/user/" + uuid + "/topic/onlineStatus", selfStatus);

            broadcastOnlineStatus(uuid, true);
            broadcastOfflineUsersToConnectedUser(uuid);
            userRepository.save(user);
        }
    }
    // 연결된 사용자에게 오프라인 사용자 상태 전송
    private void broadcastOfflineUsersToConnectedUser(String connectedUuid) {
        List<ChatRoom> chatRooms = chatRoomRepository.findRoomsAndFetchParticipantsByUserUuid(connectedUuid);
        if (chatRooms == null || chatRooms.isEmpty()) return;

        for (ChatRoom chatRoom : chatRooms) {
            chatRoom.getParticipants().stream()
                    .filter(p -> !p.getUuid().equals(connectedUuid))
                    .filter(p -> !userSessions.containsKey(p.getUuid()) || userSessions.get(p.getUuid()).isEmpty())
                    .forEach(targetUser -> {
                        Map<String, Object> status = new HashMap<>();
                        String targetUuid = targetUser.getUuid();
                        status.put("uuid", targetUuid);
                        status.put("isOnline", false);
                        if (targetUser.getLastOnline() != null) {
                            long nowMillis = LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                            long lastTimeMillis = targetUser.getLastOnline().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                            long minutesAgo = (nowMillis - lastTimeMillis) / 60000;
                            status.put("lastOnline", lastTimeMillis);
                            status.put("lastOnlineRelative", minutesAgo >= 1 ? minutesAgo + "분 전" : "방금 전");
                        }
                        messagingTemplate.convertAndSend("/user/" + connectedUuid + "/topic/onlineStatus", status);
                    });
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
                user.setLastOnline(LocalDateTime.now());
                userRepository.save(user);
                broadcastOnlineStatus(uuid, false);
                System.out.println("All sessions closed, broadcasting offline status for user: " + uuid);
            } else {
                System.out.println("User " + uuid + " still has active sessions: " + sessions);
            }
        }
    }
    // 상태 브로드캐스트 (실시간 전송)
    private void broadcastOnlineStatus(String uuid, boolean isOnline) {
        List<ChatRoom> chatRooms = chatRoomRepository.findRoomsAndFetchParticipantsByUserUuid(uuid);
        if (chatRooms == null || chatRooms.isEmpty()) {
            System.out.println("No chat rooms found for user: " + uuid + ", cannot broadcast status");
            return;
        }

        Optional<SiteUser> getUser = userRepository.findByUuid(uuid);
        if (getUser.isPresent()) {
            SiteUser user = getUser.get();
            System.out.println("Broadcasting status for user: " + uuid + ", email: " + user.getEmail() + ", isOnline: " + isOnline);
        }

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

        for (ChatRoom chatRoom : chatRooms) {
            chatRoom.getParticipants().forEach(targetUser -> { // 자신 포함
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
    // onlineStatus (수동 요청 시 사용)
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

        // 모든 참여자의 상태 반환
        chatRoom.getParticipants().forEach(targetUser -> {
            Map<String, Object> response = new HashMap<>();
            String targetUuid = targetUser.getUuid();
            boolean isOnline = userSessions.containsKey(targetUuid) && !userSessions.get(targetUuid).isEmpty();
            response.put("uuid", targetUuid);
            response.put("isOnline", isOnline);
            if (!isOnline && targetUser.getLastOnline() != null) {
                Long lastTime = targetUser.getLastOnline().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                response.put("lastOnline", lastTime);
            }
            System.out.println("Sending online status to " + currentUser.getUuid() + ": " + response);
            messagingTemplate.convertAndSend("/user/" + currentUser.getUuid() + "/topic/onlineStatus", response);
        });
    }
    // 초기 상태 요청 (로그인 시 모든 관련 사용자 상태 확인)
    @MessageMapping("/initialStatus")
    @Transactional(readOnly = true)
    public void initialStatus(Principal principal) {
        SiteUser currentUser = getCurrentUser(principal);
        String uuid = currentUser.getUuid();
        broadcastOfflineUsersToConnectedUser(uuid); // 초기 상태로 오프라인 사용자 전송
        List<ChatRoom> chatRooms = chatRoomRepository.findRoomsAndFetchParticipantsByUserUuid(currentUser.getUuid());
        chatRooms.forEach(room -> {
            room.getParticipants().stream()
                    .filter(p -> !p.getUuid().equals(currentUser.getUuid()))
                    .forEach(targetUser -> {
                        Map<String, Object> status = new HashMap<>();
                        status.put("uuid", targetUser.getUuid());
                        boolean isOnline = userSessions.containsKey(targetUser.getUuid()) && !userSessions.get(targetUser.getUuid()).isEmpty();
                        status.put("isOnline", isOnline);
                        if (!isOnline && targetUser.getLastOnline() != null) {
                            Long lastTime = targetUser.getLastOnline().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                            status.put("lastOnline", lastTime);
                        }
                        messagingTemplate.convertAndSend("/user/" + currentUser.getUuid() + "/topic/onlineStatus", status);
                    });
        });
    }
    @MessageMapping("/getMessageCount" )
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

        // 그룹 채팅에서는 차단 불가
        if ("GROUP".equals(chatRoom.getType())) {
            messagingTemplate.convertAndSend("/user/" + currentUser.getUuid() + "/topic/errors", "그룹 채팅에서는 차단 기능을 사용하실 수 없습니다.");
            return;
        }

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
        ChatRoom chatRoom = chatRoomService.findChatRoomById(request.getChatRoomId());
        // 모임장인 경우 나가기 불가
        if ("GROUP".equals(chatRoom.getType()) && chatRoom.getOwner().getUuid().equals(currentUser.getUuid())) {
            messagingTemplate.convertAndSend("/user/" + currentUser.getUuid() + "/topic/errors", "모임장은 채팅방을 나갈 수 없습니다.");
            return;
        }
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
        messageDto.getSender().setProfileImage(sender.getProfileImage());
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
                message.getTimestamp().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                message.getId() // 메시지 ID 추가
        );
        // 디버깅 로그 추가
        System.out.println("Sending notification: chatRoomId=" + notification.getChatRoomId() +
                ", senderName=" + notification.getSenderName() +
                ", content=" + notification.getContent() +
                ", timestamp=" + notification.getTimestamp() +
                ", messageId=" + notification.getMessageId());
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