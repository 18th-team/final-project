package com.team.controller;

import com.team.dto.DistrictDto;
import com.team.entity.*;
import com.team.repository.DistrictRepository;
import com.team.repository.MoimRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Controller
public class MoimController {
    private final MoimRepository moimRepository;
    private final DistrictRepository districtRepository;

    public MoimController(MoimRepository moimRepository, DistrictRepository districtRepository) {
        this.moimRepository = moimRepository;
        this.districtRepository = districtRepository;
    }

    @GetMapping("/moim/create")
    public String showCreateForm() {
        return "createMoim";
    }

    @PostMapping("/moim/create")
    public String createMoim(
            @RequestParam("minParticipants") Integer minParticipants,
            @RequestParam("maxParticipants") Integer maxParticipants,
            @RequestParam("ageRestriction") AgeRestriction ageRestriction,
            @RequestParam("hasFee") Boolean hasFee,
            @RequestParam(value = "feeAmount", required = false) Integer feeAmount,
            @RequestParam(value = "feeDetails", required = false) List<FeeDetail> feeDetails,
            @RequestParam("isOnline") Boolean isOnline,
            @RequestParam(value = "district", required = false) Long districtId, // District ID로 받음
            @RequestParam("moimTheme") MoimTheme moimTheme,
            @RequestParam("images") List<MultipartFile> images,
            @RequestParam("title") String title,
            @RequestParam("content") String content,
            @RequestParam("date") LocalDate date,
            @RequestParam("time") LocalTime time) {

// District 조회
        District district = (districtId != null) ? districtRepository.findById(districtId)
                .orElse(null) : null;

        // 이미지 저장 경로 리스트
        List<String> imagePaths = new ArrayList<>();
        for (MultipartFile file : images) {
            if (!file.isEmpty()) {
                String path = saveImage(file);
                imagePaths.add(path);
            }
        }

        NewMoim newMoim = NewMoim.builder()
                .minParticipants(minParticipants)
                .maxParticipants(maxParticipants)
                .ageRestriction(ageRestriction)
                .hasFee(hasFee)
                .feeAmount(hasFee ? feeAmount : null)
                .feeDetails(hasFee ? feeDetails : null)
                .isOnline(isOnline)
                .district(isOnline ? null : district) // District 객체 설정
                .moimTheme(moimTheme)
                .images(imagePaths)
                .title(title)
                .content(content)
                .date(date)
                .time(time)
                .build();

        moimRepository.save(newMoim);
        return "redirect:/";
    }

    @GetMapping("/moim/districts")
    @ResponseBody
    public List<DistrictDto> getDistricts(@RequestParam(value = "city") String city) {
        System.out.println("Received city: " + city);
        try {
            City cityEnum = City.valueOf(city);
            List<District> districts = districtRepository.findByCity(cityEnum);
            return districts.stream()
                    .map(d -> new DistrictDto(d.getId(), d.getName()))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            System.err.println("Error in getDistricts: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    private String saveImage(MultipartFile file) {
        return "img/" + file.getOriginalFilename();
    }
}