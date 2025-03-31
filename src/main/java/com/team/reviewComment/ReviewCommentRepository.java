package com.team.reviewComment;

import com.team.reviewPost.ReviewPost;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewCommentRepository extends JpaRepository<ReviewComment, Integer> {
    List<ReviewComment> findByReviewPost(ReviewPost reviewPost);
    void deleteByReviewPost(ReviewPost reviewPost);
}
