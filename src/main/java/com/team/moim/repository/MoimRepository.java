package com.team.moim.repository;

import com.team.moim.entity.NewMoim;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MoimRepository extends JpaRepository<NewMoim, Long> {
  

}
