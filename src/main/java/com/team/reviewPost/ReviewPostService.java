package com.team.reviewPost;

import com.team.DataNotFoundException;
import com.team.moim.entity.Club;
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
public class ReviewPostService {
    private final ReviewPostRepository reviewPostRepository;

    public List<ReviewPost> findAll() {
        return reviewPostRepository.findAll(Sort.by(Sort.Direction.DESC, "createDate"));
    }

    public Page<ReviewPost> getList(int page, String keyword) {
        List<Sort.Order> sorts =  new ArrayList<>();
        sorts.add(Sort.Order.desc("create_date"));
        Pageable pageable = PageRequest.of(page, 8, Sort.by(sorts));
        return this.reviewPostRepository.findAllByKeyword(   keyword, pageable);
    }

    public ReviewPost getReviewPost(Integer id) {
        Optional<ReviewPost> orp = this.reviewPostRepository.findById(id);

        if(orp.isPresent()) {
            return orp.get();
        } else {
            throw new DataNotFoundException("ReviewPost not found");
        }
    }

    // 작성
    public void create(String title, String content, String tags, String imageURL, SiteUser user, Club club) {
        ReviewPost rp = new ReviewPost();
        rp.setTitle(title);
        rp.setContent(content);
        rp.setTags(tags);
        rp.setImageURL(imageURL);
        rp.setAuthor(user);
        rp.setCreateDate(LocalDateTime.now());
        rp.setClub(club);
        this.reviewPostRepository.save(rp);
    }

    // 수정
    public void modify(ReviewPost rp, String title, String content, String tags, String imageURL, SiteUser user, Club club) {

    }
}
