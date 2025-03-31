package com.team.reviewPost;

import com.team.feedPost.FeedPost;
import com.team.moim.entity.Club;
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

    Page<ReviewPost> findAll(Pageable pageable);

    // 전체 검색
    @Query("SELECT r FROM ReviewPost r " +
            "WHERE r.title LIKE CONCAT('%', :kw, '%') " +
            "OR r.content LIKE CONCAT('%', :kw, '%') " +
            "OR r.tags LIKE CONCAT('%', :kw, '%') " +
            "OR r.club.title LIKE CONCAT('%', :kw, '%')")
    Page<ReviewPost> findAllByKeyword(@Param("kw") String keyword, Pageable pageable);

    List<ReviewPost> findByClub(Club club);

    Club club(Club club);
}
