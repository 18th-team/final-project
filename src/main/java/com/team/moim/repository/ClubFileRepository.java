package com.team.moim.repository;

import com.team.moim.entity.Club;
import com.team.moim.entity.ClubFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClubFileRepository extends JpaRepository<ClubFileEntity, Long> {
    List<ClubFileEntity> findByClub(Club existingClub);
}
