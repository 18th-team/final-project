package com.team.moim.repository;

import com.team.moim.entity.ClubOneDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClubOneDayRepository extends JpaRepository<ClubOneDay, Long> {


}