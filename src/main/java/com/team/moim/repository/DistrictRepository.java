package com.team.moim.repository;


import com.team.moim.entity.City;
import com.team.moim.entity.District;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DistrictRepository extends JpaRepository<District, Long> {
    List<District> findByCity(City city);
}
