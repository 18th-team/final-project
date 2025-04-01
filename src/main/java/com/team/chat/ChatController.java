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
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.util.*;

@Controller
@RequiredArgsConstructor
public class ChatController {

    private final ChatRoomService chatRoomService;
    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatMessageService chatMessageService;

    @MessageMapping("/refreshChatRooms") // 추가: 새로고침 요청 처리
    @Transactional(readOnly = true)
    public void refreshChatRooms(Principal principal, @Payload ChatRequestDTO request) {
        SiteUser currentUser = getCurrentUser(principal);
        System.out.println("Received /app/refreshChatRooms for user: " + currentUser.getUuid());
        List<ChatRoomDTO> chatRooms = chatRoomService.getChatRoomsForUser(currentUser, false);
        messagingTemplate.convertAndSend("/user/" + currentUser.getUuid() + "/topic/chatrooms", chatRooms);
        System.out.println("Sent chat rooms to /user/" + currentUser.getUuid() + "/topic/chatrooms: " + chatRooms.size());
    }
    @MessageMapping("/getMessages")
    @Transactional(readOnly = true)
    public void getMessages(Principal principal, @Payload ChatRoomDTO request) {
        SiteUser currentUser = getCurrentUser(principal);
        Long chatRoomId = request.getId(); // ChatRoomDTO의 id 사용
        if (chatRoomId == null) {
            messagingTemplate.convertAndSend("/user/" + currentUser.getUuid() + "/topic/errors", "채팅방 ID가 제공되지 않았습니다.");
            return;
        }

        ChatRoom chatRoom = chatRoomService.findChatRoomById(chatRoomId);
        if (chatRoom == null) {
            messagingTemplate.convertAndSend("/user/" + currentUser.getUuid() + "/topic/errors", "존재하지 않는 채팅방입니다.");
            return;
        }

        System.out.println("Current user UUID: " + currentUser.getUuid());
        System.out.println("Chat room ID: " + chatRoomId);
        System.out.println("Participants: " + chatRoom.getParticipants().stream().map(SiteUser::getUuid).toList());

        if (!chatRoom.getParticipants().contains(currentUser)) {
            messagingTemplate.convertAndSend("/user/" + currentUser.getUuid() + "/topic/errors", "이 채팅방에 접근할 권한이 없습니다.");
            return;
        }

        List<ChatMessage> messages = chatMessageRepository.findByChatRoomOrderByTimestampAsc(chatRoom);
        List<Object> messagesWithDate = new ArrayList<>();
        DateFormat dateFormat = new SimpleDateFormat("yyyy년 MM월 dd일", Locale.KOREAN);
        String lastDate = null;

        for (ChatMessage msg : messages) {
            String currentDate = dateFormat.format(Date.from(msg.getTimestamp().atZone(ZoneId.systemDefault()).toInstant()));
            if (!currentDate.equals(lastDate)) {
                messagesWithDate.add(new DateNotificationDTO(currentDate));
                lastDate = currentDate;
            }
            ChatRoomDTO.ChatMessageDTO messageDTO = new ChatRoomDTO.ChatMessageDTO();
            messageDTO.setId(msg.getId());
            messageDTO.setChatRoomId(chatRoomId);
            // sender가 null일 경우 처리
            if (msg.getSender() != null) {
                messageDTO.setSender(new ChatRoomDTO.SiteUserDTO(msg.getSender().getUuid(), msg.getSender().getName()));
            } else {
                // 시스템 메시지일 경우 null 또는 특별한 값 설정
                messageDTO.setSender(null); // 또는 시스템용 더미 객체를 사용할 수 있음
            }
            messageDTO.setContent(msg.getContent());
            messageDTO.setTimestamp(msg.getTimestamp().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
            messageDTO.setType(msg.getType().toString());
            messagesWithDate.add(messageDTO);
        }

        System.out.println("Sending messages for chatRoomId " + chatRoomId + " to user " + currentUser.getUuid());
        messagingTemplate.convertAndSend("/user/" + currentUser.getUuid() + "/topic/messages", messagesWithDate);
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
      /*  String blockedUuid = chatRoom.getParticipants().stream()
                .filter(p -> !p.getUuid().equals(currentUser.getUuid()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("차단할 상대방을 찾을 수 없습니다."))
                .getUuid();*/
        // 차단할 상대방을 participants에서 찾음 (현재 참여자에 없어도 UUID로 식별 가능)
        String blockedUuid = chatRoom.getParticipants().stream()
                .filter(p -> !p.getUuid().equals(currentUser.getUuid()))
                .findFirst()
                .map(SiteUser::getUuid)
                .orElse(null);

        if (blockedUuid == null) {
            // 참여자에 없으면 채팅방의 requester나 owner에서 찾기
            if (chatRoom.getRequester().getUuid().equals(currentUser.getUuid())) {
                blockedUuid = chatRoom.getOwner().getUuid();
            } else if (chatRoom.getOwner().getUuid().equals(currentUser.getUuid())) {
                blockedUuid = chatRoom.getRequester().getUuid();
            } else {
                throw new IllegalArgumentException("차단할 상대방을 찾을 수 없습니다.");
            }
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

        SiteUser receiver = chatRoom.getParticipants().stream()
                .filter(p -> !p.getUuid().equals(sender.getUuid()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("수신자를 찾을 수 없습니다."));

        if (sender.getBlockedUsers().contains(receiver) || receiver.getBlockedUsers().contains(sender)) {
            messagingTemplate.convertAndSend("/user/" + sender.getUuid() + "/topic/errors", "차단 상태로 메시지를 보낼 수 없습니다.");
            return;
        }

        ChatMessage message = chatMessageService.createMessage(chatRoom, sender, content, MessageType.NORMAL);
        List<Object> messagesWithDate = checkDateChangeAndWrap(chatRoom, message);
        chatRoom.setLastMessage(message.getContent());
        chatRoom.setLastMessageTime(message.getTimestamp());
        chatRoomRepository.save(chatRoom);

        chatRoom.getParticipants().forEach(p ->
                messagingTemplate.convertAndSend("/user/" + p.getUuid() + "/topic/messages", messagesWithDate));
    }

    @EventListener
    public void onChatRoomUpdated(ChatRoomUpdateEvent event) {
        SiteUser requester = userRepository.findByUuid(event.getRequesterUuid()).orElse(null);
        SiteUser owner = userRepository.findByUuid(event.getOwnerUuid()).orElse(null);
        if (requester != null) {
            messagingTemplate.convertAndSend("/user/" + requester.getUuid() + "/topic/chatrooms",
                    chatRoomService.getChatRoomsForUser(requester, false));
        }
        if (owner != null) {
            messagingTemplate.convertAndSend("/user/" + owner.getUuid() + "/topic/chatrooms",
                    chatRoomService.getChatRoomsForUser(owner, false));
        }
    }

    private SiteUser getCurrentUser(Principal principal) {
        if (principal == null) {
            System.err.println("Unauthorized access attempt detected");
            throw new SecurityException("인증되지 않은 사용자입니다.");
        }
        Authentication auth = (Authentication) principal;
        if (!(auth.getPrincipal() instanceof CustomSecurityUserDetails)) {
            throw new SecurityException("잘못된 사용자 정보입니다.");
        }
        CustomSecurityUserDetails userDetails = (CustomSecurityUserDetails) auth.getPrincipal();
        return userRepository.findByUuidWithBlockedUsers(userDetails.getSiteUser().getUuid())
                .orElseThrow(() -> new SecurityException("사용자를 찾을 수 없습니다: " + userDetails.getSiteUser().getUuid()));
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

    @MessageExceptionHandler(IllegalArgumentException.class)
    public void handleIllegalArgument(Principal principal, IllegalArgumentException e) {
        messagingTemplate.convertAndSend("/user/" + principal.getName() + "/topic/errors", "잘못된 요청: " + e.getMessage());
    }
}