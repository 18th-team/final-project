package com.team.moim.repository;

import com.team.moim.entity.Club;
import com.team.moim.entity.Keyword;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface ClubRepository extends JpaRepository<Club, Long> {
    // Keyword의 name 값을 기준으로 Club 검색
    List<Club> findByKeywords_NameIn(List<String> selectedThemes);


    @Query("SELECT c FROM Club c JOIN c.keywords k WHERE k.name IN :keywords GROUP BY c HAVING COUNT(k) > 0")
    List<Club> findByKeywords(@Param("keywords") List<String> keywords);

    List<Club> findByKeywords_Name(String keywordName); // 특정 키워드를 가진 클럽 조회
}