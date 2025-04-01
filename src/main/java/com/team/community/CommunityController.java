package com.team.community;

import com.team.post.Post;
import com.team.post.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@RequiredArgsConstructor
@Controller
public class CommunityController {

    private final PostService postService;

    @GetMapping("/community/post")
    public String communityPage(Model model) {
        List<Post> feedList = postService.findFeedOnly(0, 4); // 초기 4개 로드
        model.addAttribute("postList", feedList);
        model.addAttribute("hasMore", postService.countFeedOnly() > 4);
        return "post_feed_list";
    }
}

