package com.team.post;

import com.team.moim.entity.Club;
import com.team.user.SiteUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PostRepository extends JpaRepository<Post, Integer> {

    List<Post> findAllByOrderByCreateDateDesc();

    Post findByTitle(String title);
    Post findByContent(String content);
    Post findByTags(String tags);

    Page<Post> findAll(Pageable pageable);

    // 전체 검색 (제목, 내용, 태그, 클럽 제목 포함)
    @Query("SELECT p FROM Post p " +
            "LEFT JOIN p.club c " +
            "WHERE p.title LIKE %:kw% " +
            "OR p.content LIKE %:kw% " +
            "OR p.tags LIKE %:kw% " +
            "OR (c IS NOT NULL AND c.title LIKE %:kw%)")
    Page<Post> findAllByKeyword(@Param("kw") String keyword, Pageable pageable);

    Page<Post> findByBoardType(BoardType boardType, Pageable pageable);

    long countByBoardType(BoardType boardType);

    List<Post> findByClub(Club club);

    List<Post> findByAuthor(SiteUser user);// 특정 모임에 대한 후기 목록
}
