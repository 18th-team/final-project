package com.team.moim.repository;

import com.team.moim.entity.NewMoim;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MoimRepository extends JpaRepository<NewMoim, Long> {
    
}
