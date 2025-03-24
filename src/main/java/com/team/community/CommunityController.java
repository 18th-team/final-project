package com.team.community;

import com.team.feedPost.FeedPost;
import com.team.feedPost.FeedPostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@RequiredArgsConstructor
public class CommunityController {

    private final FeedPostRepository feedPostRepository;

    @GetMapping
    public String communityPage(Model model) {
        List<FeedPost> feedList = feedPostRepository.findAllByOrderByCreateDateDesc();
        model.addAttribute("feedList", feedList);
        return "feed_list"; // community.html로 이동
    }
}
