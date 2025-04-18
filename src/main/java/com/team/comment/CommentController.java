package com.team.comment;

import com.team.post.Post;
import com.team.post.PostService;
import com.team.user.SiteUser;
import com.team.user.UserService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
@RequestMapping("/comment")
public class CommentController {

    private final CommentService commentService;
    private final PostService postService;
    private final UserService userService;

    @PostMapping
    public void create(@RequestParam Long postID, @RequestParam String content,
                       Principal principal, HttpServletResponse response) {
        if (principal == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        SiteUser user = userService.getUserByUuid(principal.getName());
        Post post = postService.getPost(postID.intValue());

        commentService.create(post, user, content, null);
        response.setStatus(HttpServletResponse.SC_OK);
    }

    @PostMapping("/reply")
    public void reply(@RequestParam Long postId, @RequestParam Long parentId, @RequestParam String content,
                      Principal principal, HttpServletResponse response) {
        if (principal == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        SiteUser user = userService.getUserByUuid(principal.getName());
        Post post = postService.getPost(postId.intValue());
        Comment parent = commentService.getById(parentId);

        commentService.create(post, user, content, parent);
        response.setStatus(HttpServletResponse.SC_OK);
    }

    @PostMapping("/edit/{id}")
    public void editComment(@PathVariable("id") Long commentId, @RequestParam("content") String content,
                            Principal principal, HttpServletResponse response) {
        SiteUser user = userService.getUserByUuid(principal.getName());
        Comment comment = commentService.getById(commentId);

        if (!comment.getAuthor().getId().equals(user.getId())) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        commentService.edit(comment, content);
        response.setStatus(HttpServletResponse.SC_OK);
    }


    @PostMapping("/delete/{id}")
    public void delete(@PathVariable Long id, Principal principal, HttpServletResponse response) {
        if (principal == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        Comment comment = commentService.getById(id);
        SiteUser user = userService.getUserByUuid(principal.getName());

        if (!comment.getAuthor().getId().equals(user.getId())) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        commentService.delete(comment);
        response.setStatus(HttpServletResponse.SC_OK);
    }

}
