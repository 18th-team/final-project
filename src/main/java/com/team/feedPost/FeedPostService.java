package com.team.feedPost;

import com.team.DataNotFoundException;
import com.team.user.SiteUser;
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
public class FeedPostService {
    private final FeedPostRepository feedPostRepository;

    public List<FeedPost> findAll() {
        return feedPostRepository.findAll(Sort.by(Sort.Direction.DESC, "createDate"));
    }

    public Page<FeedPost> getList(int page, String keyword) {
        List<Sort.Order> sorts = new ArrayList<Sort.Order>();
        sorts.add(Sort.Order.desc("create_date"));
        Pageable pageable = PageRequest.of(page, 8, Sort.by(sorts));
        return this.feedPostRepository.findAllByKeyword(keyword, pageable);
    }

    public FeedPost getFeedPost(Integer id) {
        Optional<FeedPost> ofp = this.feedPostRepository.findById(id);

        if(ofp.isPresent()) {
            return ofp.get();
        } else {
            throw new DataNotFoundException("FeedPost not found");
        }
    }

    // 작성
    public void create(String title, String content, String tags, String imageURL, SiteUser user) {
        FeedPost fp = new FeedPost();
        fp.setTitle(title);
        fp.setContent(content);
        fp.setTags(tags);
        fp.setImageURL(imageURL);
        fp.setAuthor(user);
        fp.setCreateDate(LocalDateTime.now());
        this.feedPostRepository.save(fp);
    }

    // 수정
    public void modify(FeedPost fp, String title, String content) {
        fp.setTitle(title);
        fp.setContent(content);
        this.feedPostRepository.save(fp);
    }

    // 삭제
    public void delete(FeedPost fp) {
        this.feedPostRepository.delete(fp);
    }

    // 좋아요
    public void vote(FeedPost post, SiteUser user) {
        post.getVoter().add(user);
        feedPostRepository.save(post);
    }

    // 좋아요 취소
    public void cancelVote(FeedPost post, SiteUser user) {
        post.getVoter().remove(user);
        feedPostRepository.save(post);
    }




    /*
    // 검색
    private Specification<FeedPost> search(String keyword) {
        return new Specification<FeedPost>() {
            private static final long serialVersionUID = 1L;

            @Override
            public Predicate toPredicate(Root<FeedPost> fp, CriteriaQuery<?> query, CriteriaBuilder cb) {
                query.distinct(true); // 중복 제거

                // 조인
                Join<FeedPost, SiteUser> fs =  fp.join("author", JoinType.LEFT);
            }
        }
    }
     */
}
