package com.team.moim;

import com.team.moim.dto.DistrictDto;
import com.team.moim.repository.DistrictRepository;
import com.team.moim.repository.MoimRepository;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@Transactional
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
            @RequestParam(value = "city", required = false) City city,
            @RequestParam(value = "district", required = false) Long districtId,
            @RequestParam("moimTheme") MoimTheme moimTheme,
            @RequestParam("images") List<MultipartFile> images, // 여러 개의 파일을 받을 수 있도록 유지
            @RequestParam("title") String title,
            @RequestParam("content") String content,
            @RequestParam("date") LocalDate date,
            @RequestParam("time") LocalTime time) {


// 이미지 저장 및 경로 리스트 생성
        List<String> imagePaths = images.stream()
                .filter(file -> !file.isEmpty()) // 빈 파일 필터링
                .map(this::saveImage) // 파일 저장 후 경로 반환
                .filter(path -> path != null) // 저장 실패한 파일 제외
                .collect(Collectors.toList());

        System.out.println("Saved image paths: " + imagePaths);

        District district = null;
        if (!isOnline) {
            if (districtId == null) {
                throw new IllegalArgumentException("오프라인 모임은 지역을 선택해야 합니다.");
            }
            district = districtRepository.findById(districtId)
                    .orElseThrow(() -> new IllegalArgumentException("Invalid district ID"));
        }

        NewMoim newMoim = NewMoim.builder()
                .minParticipants(minParticipants)
                .maxParticipants(maxParticipants)
                .ageRestriction(ageRestriction)
                .hasFee(hasFee)
                .feeAmount(hasFee ? feeAmount : null)
                .feeDetails(hasFee ? feeDetails : null)
                .isOnline(isOnline)
                .city(isOnline ? null : city)
                .district(district)
                .moimTheme(moimTheme)
                .images(imagePaths) // 3개의 이미지 경로 리스트 저장
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
        try {
            String uploadDir = "C:/upload/img/";
            java.io.File directory = new java.io.File(uploadDir);
            if (!directory.exists()) {
                directory.mkdirs();
            }
            String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
            String path = uploadDir + fileName;
            file.transferTo(new java.io.File(path));
            System.out.println("Saved image to: " + path);
            return "/img/" + fileName;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}