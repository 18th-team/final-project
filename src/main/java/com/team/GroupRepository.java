package com.team;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GroupRepository extends JpaRepository<Group, Long> {
    // 키워드 포함 검색 (LIKE 쿼리)
    @Query("SELECT g FROM Group g WHERE g.keywords LIKE %:query%")
    List<Group> findByKeywordsContaining(@Param("query") String query);
}
