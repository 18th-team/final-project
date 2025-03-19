package com.team.repository;

import com.team.entity.NewMoim;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MoimRepository extends JpaRepository<NewMoim, Long> {
    
}
