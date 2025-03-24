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
        System.out.println("Logged in user: " + userDetails.getSiteUser().getName());
        model.addAttribute("currentUser", userDetails.getSiteUser().getName());
        return "addpublicchat";
    }

    @PostMapping("/addpublicchat/requestchat")
    public String requestPersonalChat(@RequestParam("email") String email, @RequestParam("reason") String reason,
                                      @AuthenticationPrincipal CustomSecurityUserDetails userDetails) {
        if (userDetails == null) {
            System.out.println("userDetails is null in /addpublicchat/requestChat");
            return "redirect:/login";
        }
        chatRoomService.requestPersonalChat(userDetails.getSiteUser(), email, reason);
        return "redirect:/addpublicchat";
    }

    @PostMapping("/addpublicchat/creategroup")
    public String createGroupChat(@RequestParam String groupName,
                                  @AuthenticationPrincipal CustomSecurityUserDetails userDetails) {
        if (userDetails == null) {
            System.out.println("userDetails is null in /addpublicchat/createGroup");
            return "redirect:/login";
        }
        chatRoomService.createGroupChat(userDetails.getSiteUser(), groupName);
        return "redirect:/chat";
    }
}