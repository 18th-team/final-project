package com.team.feedComment;

import com.team.DataNotFoundException;
import com.team.feedPost.FeedPost;
import com.team.user.SiteUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.AccessDeniedException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FeedCommentService {
    private final FeedCommentRepository feedCommentRepository;

    public void create(String content, SiteUser author, FeedPost feedPost) {
        FeedComment feedComment = new FeedComment();
        feedComment.setContent(content);
        feedComment.setAuthor(author);
        feedComment.setFeedPost(feedPost);
        feedComment.setCreateDate(LocalDateTime.now());
        feedCommentRepository.save(feedComment);
    }

    public List<FeedComment> getCommentsByFeedPost(FeedPost feedPost) {
        return feedCommentRepository.findByFeedPost(feedPost);
    }

    public Map<Long, List<FeedComment>> getAllCommentsMap(List<FeedPost> feedList) {
        Map<Long, List<FeedComment>> map = new HashMap<>();
        for (FeedPost feed : feedList) {
            map.put(feed.getPostID(), this.getCommentsByFeedPost(feed));
        }

        return map;
    }

    public Map<Long, Integer> getCommentCountMap(List<FeedPost> feedList) {
        Map<Long, Integer> countMap = new HashMap<>();
        for(FeedPost feed : feedList) {
            int count = feedCommentRepository.findByFeedPost(feed).size();
            countMap.put(feed.getPostID(), count);
        }

        return countMap;
    }

    public void delete(Integer commentId, SiteUser user) {
        FeedComment comment = feedCommentRepository.findById(commentId)
                .orElseThrow(() -> new DataNotFoundException("댓글 없음"));


        feedCommentRepository.delete(comment);
    }

    public void createReply(String content, SiteUser user, FeedPost post, FeedComment parent) {
        FeedComment reply = new FeedComment();
        reply.setContent(content);
        reply.setAuthor(user);
        reply.setFeedPost(post);
        reply.setParent(parent);
        reply.setCreateDate(LocalDateTime.now());
        feedCommentRepository.save(reply);
    }

    public FeedComment getComment(Integer id) {
        return feedCommentRepository.findById(id)
                .orElseThrow(() -> new DataNotFoundException("댓글을 찾을 수 없습니다."));
    }

}
