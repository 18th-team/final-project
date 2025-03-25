package com.team.feedPost;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FeedPostRepository extends JpaRepository<FeedPost, Integer> {

    List<FeedPost> findAllByOrderByCreateDateDesc();

    // 제목으로 검색
    FeedPost findByTitle(String title);

    // 내용으로 검색
    FeedPost findByContent(String content);

    // 태그로 검색
    FeedPost findByTags (String tags);

    // 전체 검색 (제목, 내용, 태그)
    @Query("SELECT f FROM FeedPost f " +
            "WHERE f.title LIKE CONCAT('%', :kw, '%') " +
            "OR f.content LIKE CONCAT('%', :kw, '%') " +
            "OR f.tags LIKE CONCAT('%', :kw, '%')")
    Page<FeedPost> findAllByKeyword(@Param("kw") String keyword, Pageable pageable);

}
