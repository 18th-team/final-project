package com.team.feedpost;

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

    public void create(String title, String content, String tags, SiteUser user) {
        FeedPost fp = new FeedPost();
        fp.setTitle(title);
        fp.setContent(content);
        fp.setCreateDate(LocalDateTime.now());
        fp.setTags(tags);
        fp.setAuthor(user.getName());
    }
}
