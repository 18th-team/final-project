package com.team.chat;

import com.team.user.CustomSecurityUserDetails;
import com.team.user.SiteUser;
import com.team.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
        model.addAttribute("currentUser", userDetails.getSiteUser().getUuid());
        return "addpublicchat";
    }

    @PostMapping(value = "/addpublicchat/requestchat", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @ResponseBody
    public ResponseEntity<?> requestPersonalChat(@RequestParam("uuid") String uuid,
                                                 @RequestParam("reason") String reason,
                                                 @AuthenticationPrincipal CustomSecurityUserDetails userDetails) {
        if (userDetails == null) {
            System.err.println("userDetails is null in /addpublicchat/requestchat");
            return ResponseEntity.status(401).body("로그인이 필요합니다.");
        }
        try {
            chatRoomService.requestPersonalChat(userDetails, uuid, reason);
            return ResponseEntity.ok("채팅 요청이 성공적으로 처리되었습니다.");
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            System.err.println("Error requesting personal chat for user " + userDetails.getSiteUser().getName() + ": " + e.getMessage());
            return ResponseEntity.status(500).body("요청 처리 중 오류 발생: " + e.getMessage());
        }
    }
}