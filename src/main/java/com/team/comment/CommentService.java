package com.team.comment;

import com.team.DataNotFoundException;
import com.team.post.Post;
import com.team.user.SiteUser;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CommentService {
    private final CommentRepository commentRepository;

    public List<Comment> getCommentsByPost(Post post) {
        return commentRepository.findByPostOrderByCreateDateAsc(post);
    }

    public Comment create(Post post, SiteUser author, String content, Comment parent) {
        Comment comment = new Comment();
        comment.setPost(post);
        comment.setAuthor(author);
        comment.setContent(content);
        comment.setCreateDate(LocalDateTime.now());
        comment.setParent(parent);
        return commentRepository.save(comment);
    }

    public void update(Comment comment, String content) {
        comment.setContent(content);
        comment.setModifyDate(LocalDateTime.now());
        commentRepository.save(comment);
    }

    @Transactional
    public void edit(Comment comment, String newContent) {
        comment.setContent(newContent);
        commentRepository.save(comment);
    }


    public void delete(Comment comment) {
        commentRepository.delete(comment);
    }

    public Comment getById(Long id) {
        return commentRepository.findById(id)
                .orElseThrow(() -> new DataNotFoundException("Comment not found"));
    }

    public List<Comment> findByAuthor(SiteUser user) {
        return commentRepository.findByAuthor(user);
    }
}
