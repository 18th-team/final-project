package com.team.mypage;

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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/mypage")
@RequiredArgsConstructor
public class MyPageController {

    private final UserService siteUserService;
    private final PostService postService;
    private final CommentService commentService;
    private final ClubService clubService;

    @GetMapping
    public String showMyPage(@AuthenticationPrincipal SiteUser user, Model model) {

        model.addAttribute("user", user);
        return "mypage";
    }
}
