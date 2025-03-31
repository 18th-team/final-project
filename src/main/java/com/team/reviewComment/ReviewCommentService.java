package com.team.reviewComment;

import com.team.DataNotFoundException;
import com.team.feedComment.FeedComment;
import com.team.feedPost.FeedPost;
import com.team.reviewPost.ReviewPost;
import com.team.user.SiteUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReviewCommentService {
    private final ReviewCommentRepository reviewCommentRepository;

    public void create(String content, SiteUser author, ReviewPost reviewPost) {
        ReviewComment reviewComment = new ReviewComment();
        reviewComment.setContent(content);
        reviewComment.setAuthor(author);
        reviewComment.setReviewPost(reviewPost);
        reviewComment.setCreateDate(LocalDateTime.now());
        reviewCommentRepository.save(reviewComment);
    }

    public List<ReviewComment> getCommentsByReviewPost(ReviewPost reviewPost) {
        return reviewCommentRepository.findByReviewPost(reviewPost);
    }

    public Map<Long, List<ReviewComment>> getAllCommentsMap(List<ReviewPost> reviewList) {
        Map<Long, List<ReviewComment>> map = new HashMap<>();
        for (ReviewPost review : reviewList) {
            map.put(review.getPostID(), this.getCommentsByReviewPost(review));
        }

        return map;
    }

    public Map<Long, Integer> getCommentCountMap(List<ReviewPost> reviewList) {
        Map<Long, Integer> countMap = new HashMap<>();
        for(ReviewPost review : reviewList) {
            int count = reviewCommentRepository.findByReviewPost(review).size();
            countMap.put(review.getPostID(), count);
        }

        return countMap;
    }

    public void delete(Integer commentId, SiteUser user) {
        ReviewComment comment = reviewCommentRepository.findById(commentId)
                .orElseThrow(() -> new DataNotFoundException("댓글 없음"));


        reviewCommentRepository.delete(comment);
    }

    public void createReply(String content, SiteUser user, ReviewPost post, ReviewComment parent) {
        ReviewComment reply = new ReviewComment();
        reply.setContent(content);
        reply.setAuthor(user);
        reply.setReviewPost(post);
        reply.setParent(parent);
        reply.setCreateDate(LocalDateTime.now());
        reviewCommentRepository.save(reply);
    }

    public ReviewComment getComment(Integer id) {
        return reviewCommentRepository.findById(id)
                .orElseThrow(() -> new DataNotFoundException("댓글을 찾을 수 없습니다."));
    }

}
