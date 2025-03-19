package com.team.repository;

import com.team.entity.City;
import com.team.entity.District;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DistrictRepository extends JpaRepository<District, Long> {
    List<District> findByCity(City city);
}
