package com.team.moim.service;

import com.team.moim.repository.ClubOneDayRepository;
import org.springframework.stereotype.Service;

@Service
public class ClubOneDayService {
    private final ClubOneDayRepository clubOneDayRepository;


    public ClubOneDayService(ClubOneDayRepository clubOneDayRepository) {
        this.clubOneDayRepository = clubOneDayRepository;
    }
}