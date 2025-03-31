package com.team.reviewComment;

import com.team.feedPost.FeedPost;
import com.team.reviewPost.ReviewPost;
import com.team.reviewPost.ReviewPostService;
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
public class ReviewCommentController {

    private final ReviewCommentService reviewCommentService;
    private final ReviewPostService reviewPostService;
    private final UserService userService;

    @PostMapping("/comment/review")
    @ResponseBody
    public String createComment(@RequestParam("postID") Long postID, @RequestParam("content") String content, Principal principal) {
        SiteUser user = userService.getUser(principal.getName());
        ReviewPost reviewPost = reviewPostService.getReviewPost(postID.intValue());

        reviewCommentService.create(content, user, reviewPost);

        return "redirect:/review/list";
    }

    @PostMapping("/comment/review/delete/{id}")
    public String deleteComment(@PathVariable Integer id,
                                Principal principal) {
        SiteUser user = userService.getUser(principal.getName());
        reviewCommentService.delete(id, user);
        return "redirect:/review/list"; // 또는 referer로 리디렉션
    }

    @PostMapping("/comment/review/reply")
    @ResponseBody
    public String createReply(@RequestParam("postId") Long postId,
                              @RequestParam("parentId") Long parentId,
                              @RequestParam("content") String content,
                              Principal principal) {
        SiteUser user = userService.getUser(principal.getName());
        ReviewPost post = reviewPostService.getReviewPost(postId.intValue());
        ReviewComment parent = reviewCommentService.getComment(parentId.intValue());
        reviewCommentService.createReply(content, user, post, parent);
        return "ok";
    }

}
