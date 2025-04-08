package com.team.mypage;

import com.team.FileService;
import com.team.comment.CommentService;
import com.team.moim.service.ClubService;
import com.team.post.PostService;
import com.team.user.CustomSecurityUserDetails;
import com.team.user.CustomUserDetailsService;
import com.team.user.SiteUser;
import com.team.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;

@Controller
@RequestMapping("/mypage")
@RequiredArgsConstructor
public class MyPageController {

    private final UserService userService;
    private final FileService fileService;

    @GetMapping
    public String mypage(Model model, Principal principal) {
        SiteUser user = userService.getUserByUuid(principal.getName());
        model.addAttribute("user", user);
        return "mypage";
    }

    @PostMapping("/update")
    public String updateUser(@RequestParam("name") String name,
                             @RequestParam("phone") String phone,
                             @RequestParam("introduction") String introduction,
                             @RequestParam(value = "profileImage", required = false) MultipartFile profileImage,
                             Principal principal) {
        SiteUser currentUser = userService.getUserByUuid(principal.getName());

        currentUser.setName(name);
        currentUser.setPhone(phone);
        currentUser.setIntroduction(introduction);

        if (profileImage != null && !profileImage.isEmpty()) {
            String imagePath = fileService.saveImage(profileImage);
            currentUser.setProfileImage(imagePath);
        }

        userService.save(currentUser);
        return "redirect:/mypage";
    }
}
