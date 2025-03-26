package com.team.chat;

import com.team.user.CustomSecurityUserDetails;
import com.team.user.SiteUser;
import com.team.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class AddPublicChatController {

    private final ChatRoomService chatRoomService;
    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepository;

    @GetMapping("/addpublicchat")
    public String addPublicChatPage(Model model, @AuthenticationPrincipal CustomSecurityUserDetails userDetails) {
        if (userDetails == null) {
            System.out.println("userDetails is null in /addpublicchat");
            return "redirect:/login";
        }
        System.out.println("Logged in user: " + userDetails.getSiteUser().getUuid());
        model.addAttribute("currentUser", userDetails.getSiteUser().getUuid()); // uuid로 변경
        return "addpublicchat";
    }

    @PostMapping("/addpublicchat/requestchat")
    public String requestPersonalChat(@RequestParam("uuid") String uuid, // email -> uuid
                                      @RequestParam("reason") String reason,
                                      @AuthenticationPrincipal CustomSecurityUserDetails userDetails) {
        if (userDetails == null) {
            System.out.println("userDetails is null in /addpublicchat/requestchat");
            return "redirect:/login";
        }
        try {
            System.out.println("Requesting personal chat with uuid: " + uuid + ", reason: " + reason);
            ChatRoom chatRoom = chatRoomService.requestPersonalChat(userDetails, uuid, reason); // uuid로 전달
            SiteUser requester = userDetails.getSiteUser();
            SiteUser receiver = userRepository.findByUuid(uuid) // uuid로 조회
                    .orElseThrow(() -> new IllegalArgumentException("수신자 조회 실패: " + uuid));
            List<ChatRoomDTO> requesterChatRooms = chatRoomService.getChatRoomsForUser(requester);
            List<ChatRoomDTO> receiverChatRooms = chatRoomService.getChatRoomsForUser(receiver);

            System.out.println("Sending to requester: " + requester.getUuid() + ", Chat rooms: " + requesterChatRooms.size());
            System.out.println("Sending to receiver: " + uuid + ", Chat rooms: " + receiverChatRooms.size());

            messagingTemplate.convertAndSend("/user/" + requester.getUuid() + "/topic/chatrooms", requesterChatRooms);
            messagingTemplate.convertAndSend("/user/" + receiver.getUuid() + "/topic/chatrooms", receiverChatRooms);

            return "redirect:/addpublicchat";
        } catch (Exception e) {
            System.out.println("Error requesting personal chat for user " + userDetails.getSiteUser().getName() + ": " + e.getMessage());
            return "redirect:/addpublicchat?error=" + e.getClass().getSimpleName();
        }
    }
}