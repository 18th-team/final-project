package com.team.chat;

import com.team.moim.entity.Club;
import com.team.moim.entity.ClubFileEntity;
import com.team.moim.repository.ClubFileRepository;
import com.team.moim.repository.ClubRepository;
import com.team.user.CustomSecurityUserDetails;
import com.team.user.SiteUser;
import com.team.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatRoomService {
    private final ChatRoomRepository chatRoomRepository;
    private final UserRepository userRepository;
    private final ChatMessageService chatMessageService;
    private final ChatMessageRepository chatMessageRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ChatRoomParticipantRepository chatRoomParticipantRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ClubRepository clubRepository;
    private final ClubFileRepository clubFileRepository;

    @Transactional(readOnly = true)
    public ChatRoom findChatRoomById(Long chatRoomId) {
        return chatRoomRepository.findByIdWithParticipantsAndBlockedUsers(chatRoomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다: " + chatRoomId));
    }

    @Transactional
    public void updateChatRoom(ChatRoom chatRoom) {
        chatRoomRepository.save(chatRoom);
    }

    @Transactional
    public void leaveChatRoom(Long chatRoomId, String userUuid) {
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다: " + chatRoomId));
        SiteUser user = userRepository.findByUuidWithBlockedUsers(userUuid)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userUuid));

        if (!chatRoom.getParticipants().contains(user)) {
            throw new IllegalStateException("이 채팅방의 참여자가 아닙니다.");
        }
        Set<String> remainingParticipantUuids = chatRoom.getParticipants().stream()
                .map(SiteUser::getUuid)
                .filter(uuid -> !uuid.equals(userUuid)) // 나가는 사람 제외
                .collect(Collectors.toSet());
        chatRoom.getParticipants().remove(user);

        // 채팅방 처리 (비었으면 삭제, 아니면 상태 변경 및 시스템 메시지 발송)
        if (chatRoom.getParticipants().isEmpty() && !"GROUP".equals(chatRoom.getType())) { // 그룹 채팅은 참여자 없어도 유지 가능성 있음 (정책 확인 필요)
            // 채팅방에 아무도 없으면 메시지와 채팅방 삭제 (개인 채팅 기준)
            chatMessageRepository.deleteByChatRoom(chatRoom);
            chatRoomRepository.delete(chatRoom);
            // 이벤트 발행 불필요 (채팅방 사라짐)
            return; // 메소드 종료
        } else {
            // 개인 채팅방이고 참여자가 남아있으면 상태 변경
            if ("PRIVATE".equals(chatRoom.getType())) {
                chatRoom.setStatus("CLOSED");
            }
            // 시스템 메시지 생성 및 저장
            String content = user.getName() + "님이 채팅방을 나갔습니다.";
            ChatMessage systemMessage = chatMessageService.createMessage(chatRoom, null, content, MessageType.SYSTEM);

            // --- ★ 누락된 메시지 브로드캐스트 로직 추가 ★ ---
            // 저장된 시스템 메시지를 DTO로 변환
            ChatRoomDTO.ChatMessageDTO systemMessageDto = convertToChatMessageDTO(systemMessage); // 이 메소드가 현재 클래스 또는 주입된 서비스에 있다고 가정

            // 남아있는 참여자들에게 시스템 메시지 전송
            remainingParticipantUuids.forEach(participantUuid -> {
                String destination = "/user/" + participantUuid + "/topic/messages";
                System.out.println("[Server DEBUG] Broadcasting leave system message to " + destination);
                messagingTemplate.convertAndSend(destination, systemMessageDto);
            });
            // --- ★ 로직 추가 끝 ★ ---

            // 채팅방 정보 업데이트 (마지막 메시지 등은 시스템 메시지로 업데이트)
            chatRoom.setLastMessage(systemMessage.getContent());
            chatRoom.setLastMessageTime(systemMessage.getTimestamp());
            chatRoomRepository.save(chatRoom); // 변경된 상태 및 마지막 메시지 저장

            // 영향을 받는 사용자 목록 (남은 참여자 + 나간 사람)
            Set<String> affectedUuids = new HashSet<>(remainingParticipantUuids);
            affectedUuids.add(userUuid); // 나간 사람도 목록 갱신 필요

            // 채팅방 목록 업데이트 이벤트 발행
            eventPublisher.publishEvent(new ChatRoomUpdateEvent(affectedUuids));
        }
    }

    @Transactional
    public void blockUserInChat(Long chatRoomId, String blockerUuid, String blockedUuid) {
        ChatRoom chatRoom = findChatRoomById(chatRoomId);
        SiteUser blocker = userRepository.findByUuidWithBlockedUsers(blockerUuid)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + blockerUuid));
        SiteUser blocked = userRepository.findByUuidWithBlockedUsers(blockedUuid)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + blockedUuid));

        if (!chatRoom.getParticipants().contains(blocker)) {
            throw new IllegalStateException("이 채팅방의 참여자가 아닙니다.");
        }

        // --- 기존 차단 로직 ---
        blocker.blockUser(blocked);
        chatRoom.getParticipants().remove(blocker); // 차단한 사람도 참여자 목록에서 제거? (정책 확인 필요)

        // --- 시스템 메시지 생성 및 DTO 변환 ---
        ChatMessage systemMessage = null;
        if (chatRoom.getParticipants().isEmpty() && !"GROUP".equals(chatRoom.getType())) { // 방이 비면 삭제
            chatMessageRepository.deleteByChatRoom(chatRoom);
            chatRoomRepository.delete(chatRoom);
        } else { // 방이 유지되면 상태 변경 및 메시지 생성
            chatRoom.setStatus("BLOCKED");
            String content = blocker.getName() + "님이 " + blocked.getName() + "님을 차단했습니다.";
            systemMessage = chatMessageService.createMessage(chatRoom, null, content, MessageType.SYSTEM);
            chatRoom.setLastMessage(content); // 마지막 메시지 업데이트
            chatRoom.setLastMessageTime(systemMessage.getTimestamp());
            chatRoomRepository.save(chatRoom); // 채팅방 저장
        }

        // --- ★ 시스템 메시지 브로드캐스트 추가 (메시지가 생성된 경우) ★ ---
        if (systemMessage != null) {
            ChatRoomDTO.ChatMessageDTO systemMessageDto = convertToChatMessageDTO(systemMessage);
            String destination = "/user/" + blocked.getUuid() + "/topic/messages";
            System.out.println("[Server DEBUG] Broadcasting block system message to " + destination);
            messagingTemplate.convertAndSend(destination, systemMessageDto);
        }
        // --- ★ 로직 추가 끝 ★ ---

        // 채팅방 목록 업데이트 이벤트 발행 (영향받는 사용자 지정 필요)
        eventPublisher.publishEvent(new ChatRoomUpdateEvent(blockerUuid, blockedUuid));
    }

    @Transactional
    public ChatRoom CreateMoimChatRoom(Long clubId, String name, String ownerUuid) {
        Optional<SiteUser> userOptional = userRepository.findByUuid(ownerUuid);
        if (!userOptional.isPresent()) {
            throw new IllegalStateException("사용자를 찾을 수 없습니다");
        }
        SiteUser ownerUser = userOptional.get();

        Optional<Club> clubOptional = clubRepository.findById(clubId);
        if (!clubOptional.isPresent()) {
            throw new IllegalStateException("모임을 찾을 수 없습니다");
        }
        Club club = clubOptional.get();

        if (!club.getHost().equals(ownerUser)) {
            throw new IllegalStateException("모임장만 채팅방을 생성할 수 있습니다");
        }

        ChatRoom chatRoom = ChatRoom.builder()
                .type("GROUP")
                .name(name != null && !name.isEmpty() ? name : club.getTitle() + " 채팅방")
                .owner(ownerUser)
                .participants(new ArrayList<>())
                .participantSettings(new ArrayList<>())
                .club(club)
                .status("ACTIVE")
                .build();

        chatRoom.addParticipant(ownerUser);
        return chatRoomRepository.save(chatRoom);
    }

    @Transactional
    public void JoinMoimChatRoom(Long chatRoomId, String uuid) {
        Optional<SiteUser> userOptional = userRepository.findByUuid(uuid);
        if (userOptional.isEmpty()) {
            throw new IllegalStateException("사용자를 찾을 수 없습니다");
        }
        SiteUser joinUser = userOptional.get();

        Optional<ChatRoom> chatRoomOptional = chatRoomRepository.findById(chatRoomId);
        if (chatRoomOptional.isEmpty()) {
            throw new IllegalStateException("채팅방이 존재하지 않습니다");
        }
        ChatRoom chatRoom = chatRoomOptional.get();

        if (!"GROUP".equals(chatRoom.getType())) {
            throw new IllegalStateException("그룹 채팅방만 참여할 수 있습니다");
        }

        Club club = chatRoom.getClub();
        if (club == null || !club.getMembers().contains(joinUser)) {
            throw new IllegalStateException("해당 모임의 멤버만 채팅방에 참여할 수 있습니다");
        }

        boolean isAlreadyParticipant = chatRoom.getParticipants().stream()
                .anyMatch(p -> p.getUuid().equals(uuid));
        if (isAlreadyParticipant) {
            throw new IllegalStateException("이미 채팅방에 참여 중입니다");
        }

        chatRoom.addParticipant(joinUser);
        chatRoomRepository.save(chatRoom);

        // 시스템 메시지 생성 및 저장
        String content = joinUser.getName() + "님이 채팅방을 참가하셨습니다.";
        ChatMessage systemMessage = chatMessageService.createMessage(chatRoom, null, content, MessageType.SYSTEM);

        // 채팅방 마지막 메시지 업데이트 (시스템 메시지로)
        chatRoom.setLastMessage(content);
        chatRoom.setLastMessageTime(systemMessage.getTimestamp());
        chatRoomRepository.save(chatRoom); // 마지막 메시지 정보 저장

        // --- ★ 시스템 메시지 브로드캐스트 추가 ★ ---
        ChatRoomDTO.ChatMessageDTO systemMessageDto = convertToChatMessageDTO(systemMessage);
        // 모든 현재 참여자에게 전송 (새로 들어온 사람 포함)
        chatRoom.getParticipants().forEach(participant -> {
            String destination = "/user/" + participant.getUuid() + "/topic/messages";
            System.out.println("[Server DEBUG] Broadcasting join system message to " + destination);
            messagingTemplate.convertAndSend(destination, systemMessageDto);
        });
        // --- ★ 로직 추가 끝 ★ ---

        // 채팅방 목록 업데이트 트리거 (참여자 목록 변경되었으므로 모든 참여자에게)
        updateChatRoomForParticipants(chatRoom);
    }

    private void updateChatRoomForParticipants(ChatRoom chatRoom) {
        List<String> participantUuids = chatRoom.getParticipants().stream()
                .map(SiteUser::getUuid)
                .collect(Collectors.toList());
        System.out.println("Updating chatrooms for participants: " + participantUuids);

        for (SiteUser participant : chatRoom.getParticipants()) {
            List<ChatRoom> userChatRooms = chatRoomRepository.findRoomsAndFetchParticipantsByUserUuid(participant.getUuid());
            List<ChatRoomDTO> chatRoomDTOs = userChatRooms.stream()
                    .map(chat -> convertToDTO(chat)) // lastMessageSender 포함
                    .collect(Collectors.toList());
            String destination = "/user/" + participant.getUuid() + "/topic/chatrooms";
            System.out.println("Sending chatrooms update to " + destination + ", chatRoomDTOs: " + chatRoomDTOs);
            messagingTemplate.convertAndSend(destination, chatRoomDTOs);
        }
    }

    private ChatRoomDTO convertToDTO(ChatRoom chatRoom) {
        ChatRoomDTO dto = new ChatRoomDTO();
        dto.setId(chatRoom.getId());
        dto.setName(chatRoom.getName());
        dto.setType(chatRoom.getType());
        dto.setParticipants(chatRoom.getParticipants().stream()
                .map(p -> new ChatRoomDTO.SiteUserDTO(p.getUuid(), p.getName(), p.getProfileImage()))
                .collect(Collectors.toList()));
        dto.setOwner(chatRoom.getOwner() != null ?
                new ChatRoomDTO.SiteUserDTO(chatRoom.getOwner().getUuid(), chatRoom.getOwner().getName(), chatRoom.getOwner().getProfileImage()) : null);
        dto.setRequester(chatRoom.getRequester() != null ?
                new ChatRoomDTO.SiteUserDTO(chatRoom.getRequester().getUuid(), chatRoom.getRequester().getName(), chatRoom.getRequester().getProfileImage()) : null);
        dto.setRequestReason(chatRoom.getRequestReason());
        dto.setStatus(chatRoom.getStatus());

        // Club 이미지 설정
        if ("GROUP".equals(chatRoom.getType()) && chatRoom.getClub() != null) {
            Club club = chatRoom.getClub();
            if (club.getFileAttached() == 1) {
                Optional<ClubFileEntity> clubFile = clubFileRepository.findByClubId(club.getId());
                clubFile.ifPresent(file -> dto.setClubImage(file.getStoredFileName()));
            }
        }

        // lastMessageSender 설정
        Optional<ChatMessage> lastMessage = chatMessageRepository.findTopByChatRoomOrderByTimestampDesc(chatRoom);
        lastMessage.ifPresent(message -> {
            ChatRoomDTO.SenderDTO senderDTO = new ChatRoomDTO.SenderDTO();
            senderDTO.setUuid(message.getSender() != null ? message.getSender().getUuid() : null);
            senderDTO.setName(message.getSender() != null ? message.getSender().getName() : null);
            senderDTO.setLastMessage(message.getContent());
            senderDTO.setLastMessageTime(message.getTimestamp());
            dto.setLastMessageSender(senderDTO);
        });

        return dto;
    }

    @Transactional
    public ChatRoom requestPersonalChat(CustomSecurityUserDetails userDetails, String receiverUuid, String reason) {
        SiteUser requester = userRepository.findByUuidWithBlockedUsers(userDetails.getSiteUser().getUuid())
                .orElseThrow(() -> new IllegalArgumentException("요청자를 찾을 수 없습니다: " + userDetails.getSiteUser().getUuid()));
        SiteUser receiver = userRepository.findByUuidWithBlockedUsers(receiverUuid)
                .orElseThrow(() -> new IllegalArgumentException("수신자를 찾을 수 없습니다: " + receiverUuid));

        if (requester.getUuid().equals(receiverUuid)) {
            throw new IllegalStateException("자기 자신에게 채팅을 요청할 수 없습니다.");
        }
        if (requester.getBlockedUsers().contains(receiver)) {
            throw new IllegalStateException("당신이 상대방을 차단했습니다.");
        }
        if (receiver.getBlockedUsers().contains(requester)) {
            throw new IllegalStateException("상대방이 당신을 차단했습니다.");
        }
        if (chatRoomRepository.existsByRequesterAndOwnerAndTypeAndStatusNot(requester, receiver, "PRIVATE", "CLOSED")) {
            throw new IllegalStateException("이미 존재하는 개인 채팅 요청입니다.");
        }

        ChatRoom chatRoom = ChatRoom.builder()
                .type("PRIVATE")
                .name(receiver.getName())
                .requester(requester)
                .owner(receiver)
                .participants(new ArrayList<>())
                .participantSettings(new ArrayList<>())
                .requestReason(reason)
                .status("PENDING")
                .lastMessageTime(LocalDateTime.now())
                .build();
        chatRoom.addParticipant(requester);
        chatRoom.addParticipant(receiver);
        ChatRoom savedChatRoom = chatRoomRepository.save(chatRoom);
        eventPublisher.publishEvent(new ChatRoomUpdateEvent(requester.getUuid(), receiver.getUuid()));
        return savedChatRoom;
    }

    @Transactional
    public void handleChatRequest(SiteUser currentUser, Long chatRoomId, String action) {
        ChatRoom chatRoom = validateChatRoom(chatRoomId, currentUser);
        String requesterUuid = chatRoom.getRequester().getUuid();
        String ownerUuid = chatRoom.getOwner().getUuid();
        switch (action.toUpperCase()) {
            case "APPROVE":
                System.out.println("승인 처리 시작: chatRoomId=" + chatRoomId);
                chatRoom.setStatus("ACTIVE");
                // 시스템 메시지 생성 및 저장
                String content = currentUser.getName() + "님이 채팅을 수락하셨습니다.";
                ChatMessage systemMessage = chatMessageService.createMessage(chatRoom, null, content, MessageType.SYSTEM);

                // 채팅방 마지막 메시지 업데이트
                chatRoom.setLastMessage(content);
                chatRoom.setLastMessageTime(systemMessage.getTimestamp());
                chatRoomRepository.save(chatRoom); // 상태 및 마지막 메시지 저장

                // --- ★ 시스템 메시지 브로드캐스트 추가 ★ ---
                ChatRoomDTO.ChatMessageDTO systemMessageDto = convertToChatMessageDTO(systemMessage);
                // 요청자와 수락자 모두에게 전송
                String ownerDestination = "/user/" + ownerUuid + "/topic/messages";
                String requesterDestination = "/user/" + requesterUuid + "/topic/messages";
                System.out.println("[Server DEBUG] Broadcasting approve system message to " + ownerDestination + " and " + requesterDestination);
                messagingTemplate.convertAndSend(ownerDestination, systemMessageDto);
                messagingTemplate.convertAndSend(requesterDestination, systemMessageDto);
                // --- ★ 로직 추가 끝 ★ ---
                break;
            case "REJECT":
                chatRoomRepository.delete(chatRoom);
            case "BLOCK":
                if ("BLOCK".equals(action.toUpperCase())) {
                    SiteUser blocked = chatRoom.getRequester();
                    currentUser.blockUser(blocked);
                    userRepository.save(currentUser);
                }
                chatRoomRepository.delete(chatRoom);
                break;
            default:
                throw new IllegalArgumentException("지원하지 않는 액션: " + action);
        }
        System.out.println("이벤트 발행: requester=" + requesterUuid + ", owner=" + ownerUuid);
        eventPublisher.publishEvent(new ChatRoomUpdateEvent(requesterUuid, ownerUuid));
    }

    @Transactional(readOnly = true)
    public List<ChatRoomDTO> getChatRoomsForUser(String userUuid) {
        SiteUser user = userRepository.findByUuidWithBlockedUsers(userUuid)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userUuid));
        List<ChatRoom> chatRooms = chatRoomRepository.findByParticipantsContainingOrPendingForUser(user);
        return chatRooms.stream().map(chat -> {
            ChatRoomDTO dto = convertToDTO(chat); // lastMessageSender 제외
            long unreadCount = chatMessageRepository.findByChatRoomOrderByTimestampAsc(chat)
                    .stream()
                    .filter(msg -> msg.getSender() != null && !msg.getSender().getUuid().equals(user.getUuid()))
                    .filter(msg -> msg.getReadBy() == null || !msg.getReadBy().contains(user))
                    .count();
            dto.setUnreadCount((int) unreadCount);

            Optional<ChatMessage> lastMessageOpt = chatMessageRepository.findTopByChatRoomOrderByTimestampDesc(chat);
            if (lastMessageOpt.isPresent()) {
                ChatMessage lastMessage = lastMessageOpt.get();
                ChatRoomDTO.SenderDTO senderDTO = new ChatRoomDTO.SenderDTO();
                if (lastMessage.getType() == MessageType.SYSTEM || lastMessage.getSender() == null) {
                    senderDTO.setUuid(null);
                    senderDTO.setName(null);
                } else {
                    senderDTO.setUuid(lastMessage.getSender().getUuid());
                    senderDTO.setName(lastMessage.getSender().getName());
                }
                senderDTO.setLastMessage(lastMessage.getContent());
                senderDTO.setLastMessageTime(lastMessage.getTimestamp());
                dto.setLastMessageSender(senderDTO);
            } else {
                dto.setLastMessageSender(null);
            }

            ChatRoomParticipant participant = chatRoomParticipantRepository.findByChatRoomAndUser(chat, user)
                    .orElseThrow(() -> new IllegalStateException("참여자를 찾을 수 없습니다."));
            dto.setNotificationEnabled(participant.isNotificationEnabled());
            dto.setExpanded(participant.isExpanded());
            return dto;
        }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ChatRoomDTO.ChatMessageDTO> getMessages(ChatRoom chatRoom, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("timestamp").ascending());
        Page<ChatMessage> messagePage = chatMessageRepository.findByChatRoom(chatRoom, pageable);
        return messagePage.getContent().stream()
                .map(this::convertToChatMessageDTO)
                .collect(Collectors.toList());
    }

    private ChatRoom validateChatRoom(Long chatRoomId, SiteUser user) {
        System.out.println("유효성 검사: chatRoomId=" + chatRoomId + ", userUuid=" + user.getUuid());
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> {
                    System.out.println("채팅방 없음: " + chatRoomId);
                    return new IllegalArgumentException("채팅방을 찾을 수 없습니다: " + chatRoomId);
                });
        System.out.println("채팅방 상태=" + chatRoom.getStatus() + ", 소유자=" + chatRoom.getOwner().getUuid());
        if (!chatRoom.getOwner().getUuid().equals(user.getUuid()) || !"PENDING".equals(chatRoom.getStatus())) {
            System.out.println("검사 실패: 소유자=" + chatRoom.getOwner().getUuid().equals(user.getUuid()) + ", 상태=" + "PENDING".equals(chatRoom.getStatus()));
            throw new SecurityException("권한이 없습니다.");
        }
        return chatRoom;
    }

    public ChatRoomDTO.ChatMessageDTO convertToChatMessageDTO(ChatMessage message) {
        if (message == null) return null;
        ChatRoomDTO.ChatMessageDTO dto = new ChatRoomDTO.ChatMessageDTO();
        dto.setId(message.getId());
        dto.setChatRoomId(message.getChatRoom().getId());
        dto.setContent(message.getContent());
        dto.setTimestamp(message.getTimestamp());
        dto.setType(message.getType().name());
        if (message.getSender() != null) {
            SiteUser senderEntity = message.getSender();
            dto.setSender(new ChatRoomDTO.SiteUserDTO(
                    senderEntity.getUuid(),
                    senderEntity.getName(),
                    senderEntity.getProfileImage() // 이 값이 null이 아닌지 확인 중요
            ));
        } else {
            dto.setSender(null); // 시스템 메시지는 sender가 null
        }
        return dto;
    }

    @EventListener(ChatRoomUpdateEvent.class)
    public void handleChatRoomUpdate(ChatRoomUpdateEvent event) {
        for (String uuid : event.getAffectedUuids()) {
            List<ChatRoomDTO> chatRooms = getChatRoomsForUser(uuid);
            messagingTemplate.convertAndSend("/user/" + uuid + "/topic/chatrooms", chatRooms);
            System.out.println("Sent /topic/chatrooms to UUID: " + uuid + ", ChatRooms: " + chatRooms);
        }
    }
}