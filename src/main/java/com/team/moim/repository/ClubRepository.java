package com.team.moim.repository;

import com.team.moim.entity.Club;
import com.team.moim.entity.Keyword;
import com.team.user.SiteUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface ClubRepository extends JpaRepository<Club, Long> {

    // ✅ 특정 카테고리에 속하는 클럽 조회 (categoryId 기준)
    List<Club> findByKeywords_Id(Long keywordId);

    // Keyword의 name 값을 기준으로 Club 검색
    List<Club> findByKeywords_NameIn(List<String> selectedThemes);


    @Query("SELECT c FROM Club c JOIN c.keywords k WHERE k.name IN :keywords GROUP BY c HAVING COUNT(k) > 0")
    List<Club> findByKeywords(@Param("keywords") List<String> keywords);

    @Query("SELECT c FROM Club c WHERE " +
            "c.title LIKE %:query% OR " +
            "c.content LIKE %:query% OR " +
            "c.city LIKE %:query% OR " +
            "EXISTS (SELECT k FROM c.keywords k WHERE k.name LIKE %:query%)")
    List<Club> findBySearchQuery(String query);

    List<Club> findByMembersContaining(SiteUser user);
}