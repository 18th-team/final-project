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
    private final SimpMessagingTemplate messagingTemplate; // 추가
    private final UserRepository userRepository;

    @GetMapping("/addpublicchat")
    public String addPublicChatPage(Model model, @AuthenticationPrincipal CustomSecurityUserDetails userDetails) {
        if (userDetails == null) {
            System.out.println("userDetails is null in /addpublicchat");
            return "redirect:/login";
        }
        System.out.println("Logged in user: " + userDetails.getSiteUser().getEmail());
        model.addAttribute("currentUser", userDetails.getSiteUser().getEmail());
        return "addpublicchat";
    }

    @PostMapping("/addpublicchat/requestchat")
    public String requestPersonalChat(@RequestParam("email") String email,
                                      @RequestParam("reason") String reason,
                                      @AuthenticationPrincipal CustomSecurityUserDetails userDetails) {
        if (userDetails == null) {
            System.out.println("userDetails is null in /addpublicchat/requestchat");
            return "redirect:/login";
        }
        try {
            System.out.println("Requesting personal chat with email: " + email + ", reason: " + reason);
            ChatRoomDTO chatRoomDTO = chatRoomService.requestPersonalChat(userDetails, email, reason);
            String requesterEmail = userDetails.getSiteUser().getEmail();
            SiteUser receiver = userRepository.findByEmail(email)
                    .orElseThrow(() -> new IllegalArgumentException("수신자 조회 실패: " + email));

            List<ChatRoomDTO> requesterChatRooms = chatRoomService.getChatRoomsForUser(userDetails.getSiteUser());
            List<ChatRoomDTO> receiverChatRooms = chatRoomService.getChatRoomsForUser(receiver);

            System.out.println("Sending to requester: " + requesterEmail + ", Chat rooms: " + requesterChatRooms.size());
            System.out.println("Sending to receiver: " + email + ", Chat rooms: " + receiverChatRooms.size());

            messagingTemplate.convertAndSend("/user/" + requesterEmail + "/topic/chatrooms", requesterChatRooms);
            messagingTemplate.convertAndSend("/user/" + email + "/topic/chatrooms", receiverChatRooms);

            return "redirect:/addpublicchat";
        } catch (Exception e) {
            System.out.println("Error requesting personal chat for user " + userDetails.getSiteUser().getName() + ": " + e.getMessage());
            return "redirect:/addpublicchat?error=" + e.getClass().getSimpleName();
        }
    }
}