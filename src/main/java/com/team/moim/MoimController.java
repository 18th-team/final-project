package com.team.moim;

import com.team.moim.dto.DistrictDto;
import com.team.moim.dto.NewMoimDto;
import com.team.moim.entity.City;
import com.team.moim.entity.District;
import com.team.moim.entity.NewMoim;
import com.team.moim.repository.DistrictRepository;
import com.team.moim.repository.MoimRepository;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
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
    public String createMoim(@ModelAttribute NewMoimDto dto, @RequestParam("images") List<MultipartFile> imageFiles) {
        List<String> imagePaths = imageFiles.stream()
                .filter(file -> !file.isEmpty())
                .map(this::saveImage)
                .collect(Collectors.toList());

        District district = dto.getDistrictId() != null
                ? districtRepository.findById(dto.getDistrictId()).orElse(null)
                : null;

        NewMoim newMoim = NewMoim.builder()
                .city(dto.getCity())
                .minParticipants(dto.getMinParticipants())
                .maxParticipants(dto.getMaxParticipants())
                .ageRestriction(dto.getAgeRestriction())
                .hasFee(dto.getHasFee())
                .feeAmount(dto.getHasFee() ? dto.getFeeAmount() : null)
                .feeDetails(dto.getHasFee() ? dto.getFeeDetails() : null)
                .isOnline(dto.getIsOnline())
                .district(dto.getIsOnline() ? null : district)
                .moimTheme(dto.getMoimTheme())
                .images(imagePaths)
                .title(dto.getTitle())
                .content(dto.getContent())
                .date(dto.getDate())
                .time(dto.getTime())
                .build();

        moimRepository.save(newMoim);
        return "redirect:/moim/" + newMoim.getId();
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