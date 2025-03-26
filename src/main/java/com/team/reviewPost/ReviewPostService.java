package com.team.reviewPost;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@RequiredArgsConstructor
@Service
public class ReviewPostService {
    private final ReviewPostRepository reviewPostRepository;

    public List<ReviewPost> findAll() {
        return reviewPostRepository.findAll(Sort.by(Sort.Direction.DESC, "createDate"));
    }

    public Page<ReviewPost> getList(Pageable pageable) {
        return reviewPostRepository.findAll(pageable);
    }
}
