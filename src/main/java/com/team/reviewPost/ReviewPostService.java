package com.team.reviewPost;

import com.team.DataNotFoundException;
import com.team.feedPost.FeedPost;
import com.team.moim.entity.Club;
import com.team.user.SiteUser;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Service
public class ReviewPostService {
    private final ReviewPostRepository reviewPostRepository;

    public List<ReviewPost> findAll() { return reviewPostRepository.findAll(Sort.by(Sort.Direction.DESC, "createDate")); }

    public Page<ReviewPost> getList(int page, String keyword) {
        List<Sort.Order> sorts = new ArrayList<>();
        sorts.add(Sort.Order.desc("createDate"));
        Pageable pageable = PageRequest.of(page, 8, Sort.by(sorts));
        return this.reviewPostRepository.findAllByKeyword(keyword, pageable);
    }

    public ReviewPost getReviewPost(Integer id) {
        Optional<ReviewPost> orp = this.reviewPostRepository.findById(id);

        if(orp.isPresent()) {
            return orp.get();
        } else {
            throw new DataNotFoundException("Review Post Not Found");
        }
    }

    // 작성
    public void create(String title, String content, String tags, Club club, String imageURL, SiteUser user) {
        ReviewPost rp = new ReviewPost();
        rp.setTitle(title);
        rp.setContent(content);
        rp.setTags(tags);
        rp.setImageURL(imageURL);
        rp.setAuthor(user);
        rp.setClub(club);
        rp.setCreateDate(LocalDateTime.now());
        this.reviewPostRepository.save(rp);
    }

    // 수정
    public void modify(ReviewPost rp, String title, String content, String tags, Club club, String imageURL) {
        rp.setTitle(title);
        rp.setContent(content);
        rp.setTags(tags);
        rp.setImageURL(imageURL);
        rp.setClub(club);
        this.reviewPostRepository.save(rp);
    }

    // 삭제
    @Transactional
    public void delete(ReviewPost rp) {
        // reviewCommentRepository.deleteByFeedPost(rp);
        this.reviewPostRepository.delete(rp);
    }

    // 좋아요
    public void vote(ReviewPost post, SiteUser user) {
        post.getVoter().add(user);
        reviewPostRepository.save(post);
    }

    // 좋아요 취소
    public void cancelVote(ReviewPost post, SiteUser user) {
        post.getVoter().remove(user);
        reviewPostRepository.save(post);
    }

    public List<ReviewPost> findLimited(int offset, int limit) {
        Pageable pageable = PageRequest.of(offset / limit, limit, Sort.by(Sort.Direction.DESC, "createDate"));
        return reviewPostRepository.findAll(pageable).getContent();
    }

    public long count() {
        return reviewPostRepository.count();
    }

    public List<ReviewPost> searchByKeyword(String keyword, int offset, int limit) {
        Pageable pageable = PageRequest.of(offset / limit, limit, Sort.by(Sort.Direction.DESC, "createDate"));
        return reviewPostRepository.findAllByKeyword(keyword, pageable).getContent();
    }

    public long countByKeyword(String keyword) {
        return reviewPostRepository.findAllByKeyword(keyword, Pageable.unpaged()).getTotalElements();
    }
}
