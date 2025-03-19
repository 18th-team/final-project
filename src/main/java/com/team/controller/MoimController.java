package com.team.controller;

import com.team.entity.*;
import com.team.repository.MoimRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;

@Controller
public class MoimController {
    private final MoimRepository moimRepository;

    public MoimController(MoimRepository moimRepository) {
        this.moimRepository = moimRepository;
    }

    @GetMapping("/moim/create")
    public String showCreateForm() {
        return "createMoim";
    }

    @PostMapping("/moim/create")
    public String createMoim(
            @RequestParam Integer minParticipants,
            @RequestParam Integer maxParticipants,
            @RequestParam AgeRestriction ageRestriction,
            @RequestParam Boolean hasFee,
            @RequestParam(required = false) Integer feeAmount,
            @RequestParam(required = false) List<FeeDetail> feeDetails,
            @RequestParam Boolean isOnline,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String district,
            @RequestParam MoimTheme moimTheme,
            @RequestParam("images") List<MultipartFile> images,
            @RequestParam String title,
            @RequestParam String content,
            @RequestParam LocalDate date,
            @RequestParam LocalTime time) {
        City cityEnum = (city != null && !city.isEmpty()) ? City.valueOf(city) : null;
        NewMoim moim = NewMoim.builder()
                .minParticipants(minParticipants)
                .maxParticipants(maxParticipants)
                .ageRestriction(ageRestriction)
                .hasFee(hasFee)
                .feeAmount(hasFee ? feeAmount : null)
                .feeDetails(hasFee ? feeDetails : null)
                .isOnline(isOnline)
                .city(isOnline ? null : cityEnum) // City 타입으로 전달
                .district(isOnline ? null : district)
                .moimTheme(moimTheme)
                .images(images.stream().map(file -> saveImage(file)).toList())
                .title(title)
                .content(content)
                .date(date)
                .time(time)
                .build();

        moimRepository.save(moim);
        return "redirect:/";
    }

    @GetMapping("/moim/districts")
    @ResponseBody
    public List<String> getDistricts(@RequestParam String city) {
        System.out.println("Received city: " + city); // 서버 로그
        try {
            switch (city) {
                case "GYEONGGI":
                    return List.of("수원시 팔달구", "수원시 장안구", "평택시", "이천시", "안양시 만안구");
                case "SEOUL":
                    return List.of("강남구", "종로구", "영등포구", "마포구");
                case "INCHEON":
                    return List.of("남동구", "연수구", "부평구");
                default:
                    return Collections.emptyList(); // 기본값으로 빈 리스트 반환
            }
        } catch (Exception e) {
            System.err.println("Error in getDistricts: " + e.getMessage());
            return Collections.emptyList(); // 예외 발생 시 빈 리스트 반환
        }
    }

    private String saveImage(MultipartFile file) {
        return "img/" + file.getOriginalFilename(); // 임시 구현
    }
}
