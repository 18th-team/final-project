package com.team.reviewPost;

import com.team.feedPost.FeedPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReviewPostRepository extends JpaRepository<ReviewPost, Integer> {

    List<ReviewPost> findAllByOrderByCreateDateDesc();

    // 제목으로 검색
    ReviewPost findByTitle(String title);

    // 내용으로 검색
    ReviewPost findByContent(String content);

    // 태그로 검색
    ReviewPost findByTags(String tags);

    // 전체 검색 (제목, 내용, 태그)
    @Query("SELECT r FROM ReviewPost r " +
            "WHERE r.title LIKE CONCAT('%', :kw, '%') " +
            "OR r.content LIKE CONCAT('%', :kw, '%') " +
            "OR r.tags LIKE CONCAT('%', :kw, '%')")
    Page<ReviewPost> findAllByKeyword(@Param("kw") String keyword, Pageable pageable);
}
