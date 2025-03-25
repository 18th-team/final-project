package com.team.chat;

import com.team.user.CustomSecurityUserDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AddPublicChatController {

    @Autowired
    private ChatRoomService chatRoomService;

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
            chatRoomService.requestPersonalChat(userDetails, email, reason);
            return "redirect:/addpublicchat";
        } catch (IllegalArgumentException e) {
            System.out.println("Error requesting personal chat for user " + userDetails.getSiteUser().getName() + ": " + e.getMessage());
            return "redirect:/addpublicchat?error=userNotFound";
        }
    }

    /*@PostMapping("/addpublicchat/creategroup")
    public String createGroupChat(@RequestParam("groupName") String groupName,
                                  @AuthenticationPrincipal CustomSecurityUserDetails userDetails) {
        if (userDetails == null) {
            System.out.println("userDetails is null in /addpublicchat/creategroup");
            return "redirect:/login";
        }
        try {
            chatRoomService.createGroupChat(userDetails.getSiteUser(), groupName);
            return "redirect:/"; // /chat 대신 /로 리다이렉트 (일관성 유지)
        } catch (Exception e) {
            System.out.println("Error creating group chat: " + e.getMessage());
            return "redirect:/addpublicchat?error=groupCreationFailed";
        }
    }*/
}