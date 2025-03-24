package com.team.chat;

import com.team.user.SiteUser;
import com.team.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
public class ChatRoomService {
    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    public List<ChatRoom> getPersonalChatRooms(SiteUser user) {
        return chatRoomRepository.findByParticipantsContaining(user);
    }

    public void broadcastChatRooms(SiteUser user) {
        List<ChatRoom> chatRooms = getPersonalChatRooms(user);
        messagingTemplate.convertAndSendToUser(user.getName(), "/topic/chatrooms", chatRooms);
    }

    // 개인 채팅 요청
    public void requestPersonalChat(SiteUser requester, String recipientEmail, String reason) {
        Optional<SiteUser> recipientOpt = userRepository.findByEmail(recipientEmail);
        if (!recipientOpt.isPresent()) {
            throw new IllegalArgumentException("해당 이메일의 사용자를 찾을 수 없습니다.");
        }
        SiteUser recipient = recipientOpt.get();

        if (recipient.getName().equals(requester.getName())) {
            throw new IllegalArgumentException("자기 자신에게 채팅 요청을 보낼 수 없습니다.");
        }

        ChatRoom existingRoom = chatRoomRepository.findByParticipantsContainingAndName(requester, recipient.getName());
        if (existingRoom != null && !existingRoom.getStatus().equals("REJECTED")) {
            return; // 이미 요청이 있거나 채팅방이 존재
        }

        ChatRoom chatRoom = new ChatRoom();
        chatRoom.setName(recipient.getName());
        chatRoom.setType("PERSONAL");
        chatRoom.setLastMessage("");
        chatRoom.setLastMessageTime(LocalDateTime.now());
        chatRoom.setParticipants(Arrays.asList(requester, recipient));
        chatRoom.setUnreadCount(0);
        chatRoom.setRequestReason(reason);
        chatRoom.setStatus("PENDING");
        chatRoomRepository.save(chatRoom);

        broadcastChatRooms(recipient);
    }

    // 모임 채팅 생성
    public ChatRoom createGroupChat(SiteUser owner, String groupName) {
        ChatRoom chatRoom = new ChatRoom();
        chatRoom.setName(groupName);
        chatRoom.setType("GROUP");
        chatRoom.setLastMessage("");
        chatRoom.setLastMessageTime(LocalDateTime.now());
        chatRoom.setParticipants(Arrays.asList(owner));
        chatRoom.setOwner(owner);
        chatRoom.setUnreadCount(0);
        chatRoom.setStatus("APPROVED"); // 모임장은 자동 승인
        return chatRoomRepository.save(chatRoom);
    }

    // 모임 채팅 가입 요청
    public void requestGroupChatJoin(SiteUser requester, Long groupId, String reason) {
        Optional<ChatRoom> chatRoomOpt = chatRoomRepository.findById(groupId);
        if (!chatRoomOpt.isPresent()) {
            throw new IllegalArgumentException("해당 모임 채팅을 찾을 수 없습니다.");
        }
        ChatRoom chatRoom = chatRoomOpt.get();

        if (!chatRoom.getType().equals("GROUP")) {
            throw new IllegalArgumentException("모임 채팅만 가입 요청이 가능합니다.");
        }
        if (chatRoom.getParticipants().contains(requester)) {
            return; // 이미 참여 중
        }

        // 기존 요청이 있는지 확인 (PENDING 상태)
        ChatRoom existingRequest = chatRoomRepository.findByParticipantsContainingAndName(requester, chatRoom.getName());
        if (existingRequest != null && existingRequest.getStatus().equals("PENDING")) {
            return; // 이미 요청 대기 중
        }

        ChatRoom request = new ChatRoom();
        request.setName(chatRoom.getName());
        request.setType("GROUP");
        request.setLastMessage("");
        request.setLastMessageTime(LocalDateTime.now());
        request.setParticipants(Arrays.asList(requester, chatRoom.getOwner()));
        request.setOwner(chatRoom.getOwner());
        request.setUnreadCount(0);
        request.setRequestReason(reason);
        request.setStatus("PENDING");
        chatRoomRepository.save(request);

        broadcastChatRooms(chatRoom.getOwner());
    }

    // 개인/모임 채팅 요청 처리
    public void handleChatRequest(SiteUser user, Long chatRoomId, String action) {
        Optional<ChatRoom> chatRoomOpt = chatRoomRepository.findById(chatRoomId);
        if (!chatRoomOpt.isPresent()) {
            throw new IllegalArgumentException("해당 채팅방을 찾을 수 없습니다.");
        }
        ChatRoom chatRoom = chatRoomOpt.get();

        SiteUser requester = chatRoom.getParticipants().stream()
                .filter(u -> !u.getName().equals(user.getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("요청자를 찾을 수 없습니다."));

        if (chatRoom.getType().equals("GROUP") && !user.equals(chatRoom.getOwner())) {
            throw new IllegalArgumentException("모임장은 요청을 처리할 수 없습니다.");
        }

        switch (action) {
            case "APPROVE":
                if (chatRoom.getType().equals("PERSONAL")) {
                    chatRoom.setStatus("APPROVED");
                    chatRoomRepository.save(chatRoom);
                    messagingTemplate.convertAndSendToUser(requester.getName(), "/topic/notification",
                            user.getName() + "님이 개인 채팅 요청을 승인하였습니다.");
                    broadcastChatRooms(requester);
                } else { // GROUP
                    Optional<ChatRoom> groupOpt = Optional.ofNullable(chatRoomRepository.findByIdAndOwner(chatRoomId, user));
                    if (groupOpt.isPresent()) {
                        ChatRoom group = groupOpt.get();
                        List<SiteUser> participants = group.getParticipants();
                        participants.add(requester);
                        group.setParticipants(participants);
                        chatRoom.setStatus("APPROVED");
                        chatRoomRepository.save(group);
                        chatRoomRepository.delete(chatRoom); // 요청 레코드 삭제
                        messagingTemplate.convertAndSendToUser(requester.getName(), "/topic/notification",
                                user.getName() + "님이 모임 가입 요청을 승인하였습니다.");
                        broadcastChatRooms(requester);
                    }
                }
                break;
            case "REJECT":
                chatRoom.setStatus("REJECTED");
                chatRoomRepository.save(chatRoom);
                messagingTemplate.convertAndSendToUser(requester.getName(), "/topic/notification",
                        user.getName() + "님이 " + (chatRoom.getType().equals("PERSONAL") ? "개인 채팅" : "모임 가입") + " 요청을 거부하였습니다.");
                break;
            case "BLOCK":
                chatRoom.setStatus("BLOCKED");
                chatRoomRepository.save(chatRoom);
                break;
        }
        broadcastChatRooms(user);
    }
}