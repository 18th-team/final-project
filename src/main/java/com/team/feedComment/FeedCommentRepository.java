package com.team.feedComment;

import com.team.feedPost.FeedPost;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FeedCommentRepository extends JpaRepository<FeedComment, Integer> {
    List<FeedComment> findByFeedPost(FeedPost feedPost);
}
