package com.team.comment;

import com.team.post.Post;
import com.team.user.SiteUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    List<Comment> findByPostOrderByCreateDateAsc(Post post);

    List<Comment> findByAuthor(SiteUser user);

    void deleteByPost(Post post);
}
