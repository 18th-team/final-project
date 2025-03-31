package com.team.chat;

import com.team.user.CustomSecurityUserDetails;
import com.team.user.SiteUser;
import com.team.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class ChatController {

    private final ChatRoomService chatRoomService;
    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatMessageService chatMessageService;
    private final ChatRoomRepository chatRoomRepository;

    @MessageMapping("/handleChatRequest")
    @Transactional
    public void handleChatRequest(Principal principal, @Payload ChatRequestDTO request) {
        SiteUser currentUser = getCurrentUser(principal);
        String action = request.getAction();
        if (!Arrays.asList("APPROVE", "REJECT", "BLOCK").contains(action)) {
            messagingTemplate.convertAndSend("/user/" + currentUser.getUuid() + "/topic/errors", "잘못된 액션입니다: " + action);
            throw new IllegalArgumentException("잘못된 액션입니다: " + action);
        }

        // 삭제 전 채팅방 조회
        ChatRoom chatRoomBeforeDelete = chatRoomRepository.findById(request.getChatRoomId())
                .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다: " + request.getChatRoomId()));
        SiteUser requester = chatRoomBeforeDelete.getRequester();
        SiteUser owner = chatRoomBeforeDelete.getOwner();

        chatRoomService.handleChatRequest(currentUser, request.getChatRoomId(), action);

        if (requester != null) {
            List<ChatRoomDTO> requesterChatRooms = chatRoomService.getChatRoomsForUser(requester);
            System.out.println("Sending to requester " + requester.getUuid() + ": " + requesterChatRooms.size());
            messagingTemplate.convertAndSend("/user/" + requester.getUuid() + "/topic/chatrooms", requesterChatRooms);
        }
        if (owner != null) {
            List<ChatRoomDTO> ownerChatRooms = chatRoomService.getChatRoomsForUser(owner);
            System.out.println("Sending to owner " + owner.getUuid() + ": " + ownerChatRooms.size());
            messagingTemplate.convertAndSend("/user/" + owner.getUuid() + "/topic/chatrooms", ownerChatRooms);
        }
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

    @MessageMapping("/sendMessage")
    @Transactional // 트랜잭션 추가
    public void sendMessage(@Payload ChatRoomDTO.ChatMessageDTO messageDTO, Principal principal) {
        SiteUser sender = getCurrentUser(principal);
        ChatRoom chatRoom = chatRoomService.findChatRoomById(messageDTO.getChatRoomId());

        // 채팅방 상태가 CLOSED 또는 BLOCKED일 경우 메시지 전송 차단
        if ("CLOSED".equals(chatRoom.getStatus()) || "BLOCKED".equals(chatRoom.getStatus())) {
            messagingTemplate.convertAndSend("/user/" + sender.getUuid() + "/topic/errors",
                    "이 채팅방은 더 이상 메시지를 보낼 수 없습니다. 상태: " + chatRoom.getStatus());
            return;
        }

        ChatMessage message = chatMessageService.createMessage(chatRoom, sender, messageDTO.getContent(), MessageType.NORMAL);

        List<Object> messagesWithDate = checkDateChangeAndWrap(chatRoom, message);
        chatRoom.setLastMessage(message.getContent());
        chatRoom.setLastMessageTime(message.getTimestamp());
        chatRoomRepository.save(chatRoom);

        // 트랜잭션 내에서 participants에 접근
        List<SiteUser> participants = new ArrayList<>(chatRoom.getParticipants());
        participants.forEach(participant ->
                messagingTemplate.convertAndSend("/user/" + participant.getUuid() + "/topic/messages", messagesWithDate));

        participants.forEach(participant ->
                messagingTemplate.convertAndSend("/user/" + participant.getUuid() + "/topic/chatrooms",
                        chatRoomService.getChatRoomsForUser(participant)));
    }

    @MessageMapping("/getMessages")
    @Transactional(readOnly = true)
    public void getMessages(@Payload Map<String, Long> payload, Principal principal) {
        try {
            SiteUser user = getCurrentUser(principal);
            Long chatRoomId = payload.get("chatRoomId");
            System.out.println("Fetching messages for chatRoomId: " + chatRoomId + " by user: " + user.getUuid());
            ChatRoom chatRoom = chatRoomService.findChatRoomById(chatRoomId);

            List<SiteUser> participants = new ArrayList<>(chatRoom.getParticipants());
            System.out.println("Participants: " + participants.size() + " - " + participants.stream()
                    .map(SiteUser::getUuid).collect(Collectors.toList()));

            boolean isParticipant = participants.stream().anyMatch(p -> p.getUuid().equals(user.getUuid()));
            if (!isParticipant) {
                System.out.println("User " + user.getUuid() + " is not a participant of chatRoomId: " + chatRoomId);
                messagingTemplate.convertAndSend("/user/" + user.getUuid() + "/topic/errors", "접근 권한이 없습니다.");
                return;
            }

            List<Object> messages = chatRoomService.getMessagesWithDateNotifications(chatRoomId);
            System.out.println("Messages fetched: " + messages.size() + " for chatRoomId: " + chatRoomId);
            System.out.println("Sending messages to /user/" + user.getUuid() + "/topic/messages: " + messages);
            messagingTemplate.convertAndSend("/user/" + user.getUuid() + "/topic/messages", messages);
        } catch (Exception e) {
            System.err.println("Error in getMessages: " + e.getMessage());
            e.printStackTrace();
            messagingTemplate.convertAndSend("/user/" + principal.getName() + "/topic/errors", "메시지 조회 실패: " + e.getMessage());
        }
    }

    private List<Object> checkDateChangeAndWrap(ChatRoom chatRoom, ChatMessage newMessage) {
        List<ChatMessage> recentMessages = chatMessageRepository.findTop2ByChatRoomOrderByTimestampDesc(chatRoom);
        DateFormat dateFormat = new SimpleDateFormat("yyyy년 MM월 dd일", Locale.KOREAN);
        List<Object> result = new ArrayList<>();
        ChatRoomDTO.ChatMessageDTO newMessageDTO = chatRoomService.convertToChatMessageDTO(newMessage);

        if (recentMessages.size() > 1) {
            ChatMessage lastMessage = recentMessages.get(1);
            String lastDate = dateFormat.format(Date.from(lastMessage.getTimestamp().atZone(ZoneId.systemDefault()).toInstant()));
            String currentDate = dateFormat.format(Date.from(newMessage.getTimestamp().atZone(ZoneId.systemDefault()).toInstant()));
            if (!lastDate.equals(currentDate)) {
                result.add(new DateNotificationDTO(currentDate));
            }
        } else if (recentMessages.isEmpty()) {
            result.add(new DateNotificationDTO(dateFormat.format(Date.from(newMessage.getTimestamp().atZone(ZoneId.systemDefault()).toInstant()))));
        }
        result.add(newMessageDTO);
        return result;
    }

    private SiteUser getCurrentUser(Principal principal) {
        if (principal == null || !(principal instanceof Authentication)) {
            throw new SecurityException("인증되지 않은 사용자입니다.");
        }
        Authentication auth = (Authentication) principal;
        if (!(auth.getPrincipal() instanceof CustomSecurityUserDetails)) {
            throw new SecurityException("잘못된 사용자 정보입니다.");
        }
        return ((CustomSecurityUserDetails) auth.getPrincipal()).getSiteUser();
    }
    @MessageMapping("/leaveChatRoom")
    @Transactional
    public void leaveChatRoom(Principal principal, @Payload ChatRequestDTO request) {
        SiteUser currentUser = getCurrentUser(principal);
        chatRoomService.leaveChatRoom(request.getChatRoomId(), currentUser.getUuid());

        // 모든 참여자에게 채팅 목록 새로고침 알림
        ChatRoom chatRoom = chatRoomService.findChatRoomById(request.getChatRoomId());
        chatRoom.getParticipants().forEach(participant -> {
            messagingTemplate.convertAndSend("/user/" + participant.getUuid() + "/topic/chatrooms",
                    chatRoomService.getChatRoomsForUser(participant));
        });

        // 나간 사용자에게도 채팅 목록 새로고침
        messagingTemplate.convertAndSend("/user/" + currentUser.getUuid() + "/topic/chatrooms",
                chatRoomService.getChatRoomsForUser(currentUser));
    }

    @MessageMapping("/blockUser")
    @Transactional
    public void blockUser(Principal principal, @Payload ChatRequestDTO request) {
        SiteUser currentUser = getCurrentUser(principal);
        ChatRoom chatRoom = chatRoomService.findChatRoomById(request.getChatRoomId());

        // 차단할 상대방 찾기 (현재 사용자 제외)
        String blockedUuid = chatRoom.getParticipants().stream()
                .filter(participant -> !participant.getUuid().equals(currentUser.getUuid()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("차단할 상대방을 찾을 수 없습니다."))
                .getUuid();

        chatRoomService.blockUserInChat(request.getChatRoomId(), currentUser.getUuid(), blockedUuid);

        // 모든 참여자에게 채팅 목록 새로고침 알림
        chatRoom.getParticipants().forEach(participant -> {
            messagingTemplate.convertAndSend("/user/" + participant.getUuid() + "/topic/chatrooms",
                    chatRoomService.getChatRoomsForUser(participant));
        });

        // 차단한 사용자에게도 채팅 목록 새로고침
        messagingTemplate.convertAndSend("/user/" + currentUser.getUuid() + "/topic/chatrooms",
                chatRoomService.getChatRoomsForUser(currentUser));
    }
    @MessageExceptionHandler
    public void handleException(Exception e) {
        System.out.println("handleException: " + e.getMessage());
        messagingTemplate.convertAndSendToUser("system", "/topic/errors", e.getMessage());
    }
}