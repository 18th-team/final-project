package com.team.feedComment;

import com.team.feedPost.FeedPost;
import com.team.feedPost.FeedPostService;
import com.team.user.SiteUser;
import com.team.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class FeedCommentController {

    private final FeedCommentService feedCommentService;
    private final FeedPostService feedPostService;
    private final UserService userService;

    @PostMapping("/comment/feed")
    @ResponseBody
    public String createComment(@RequestParam("postID") Long postID, @RequestParam("content") String content, Principal principal) {
        SiteUser user = userService.getUser(principal.getName());
        FeedPost feedPost = feedPostService.getFeedPost(postID.intValue());

        feedCommentService.create(content, user, feedPost);

        return "redirect:/feed/list";
    }

    @PostMapping("/comment/feed/delete/{id}")
    public String deleteComment(@PathVariable Integer id,
                                Principal principal) {
        SiteUser user = userService.getUser(principal.getName());
        feedCommentService.delete(id, user);
        return "redirect:/feed/list"; // 또는 referer로 리디렉션
    }

    @PostMapping("/comment/feed/reply")
    @ResponseBody
    public String createReply(@RequestParam("postId") Long postId,
                              @RequestParam("parentId") Long parentId,
                              @RequestParam("content") String content,
                              Principal principal) {
        SiteUser user = userService.getUser(principal.getName());
        FeedPost post = feedPostService.getFeedPost(postId.intValue());
        FeedComment parent = feedCommentService.getComment(parentId.intValue());
        feedCommentService.createReply(content, user, post, parent);
        return "ok";
    }

}
