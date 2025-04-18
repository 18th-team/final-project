package com.team.mypage;

import com.team.FileService;
import com.team.comment.Comment;
import com.team.comment.CommentService;
import com.team.moim.entity.Club;
import com.team.moim.service.ClubService;
import com.team.post.Post;
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
import java.util.List;

@Controller
@RequestMapping("/mypage")
@RequiredArgsConstructor
public class MyPageController {

    private final UserService userService;
    private final FileService fileService;
    private final ClubService clubService;
    private final PostService postService;
    private final CommentService commentService;

    @GetMapping
    public String mypage(Model model, Principal principal) {
        SiteUser user = userService.getUserByUuid(principal.getName());
        model.addAttribute("user", user);

        // 내가 참여한 모임
        List<Club> joinedClubs = clubService.getClubsByUser(user);
        model.addAttribute("joinedClubs", joinedClubs);

        // 내가 작성한 게시글
        List<Post> userPosts = postService.findByAuthor(user);
        model.addAttribute("userPosts", userPosts);

        // 내가 작성한 댓글
        List<Comment> userComments = commentService.findByAuthor(user);
        model.addAttribute("userComments", userComments);

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
            String imagePath = fileService.saveProfileImage(profileImage);
            currentUser.setProfileImage(imagePath);
        }

        userService.save(currentUser);
        return "redirect:/mypage";
    }
}