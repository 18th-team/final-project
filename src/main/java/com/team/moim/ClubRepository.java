package com.team.moim;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClubRepository extends JpaRepository<Club, Long> {

    // 모집 중인 모임만 찾기
    List<Club> findByStatus(String status);

    // 특정 호스트가 생성한 모임 찾기
    List<Club> findByHostId(String hostId);
}