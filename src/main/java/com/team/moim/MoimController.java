package com.team.moim;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.moim.dto.DistrictDto;
import com.team.moim.dto.NewMoimDto;
import com.team.moim.entity.City;
import com.team.moim.entity.District;
import com.team.moim.entity.NewMoim;
import com.team.moim.repository.DistrictRepository;
import com.team.moim.repository.MoimRepository;
import com.team.moim.service.MoimService;
import org.springframework.aot.hint.TypeReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@Transactional
@Controller
public class MoimController {
    private final MoimRepository moimRepository;
    private final DistrictRepository districtRepository;

    public MoimController(MoimRepository moimRepository, DistrictRepository districtRepository) {
        this.moimRepository = moimRepository;
        this.districtRepository = districtRepository;
    }

    @Autowired
    private MoimService moimService;

    @GetMapping("/moim/list")
    public String listMoim(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model) {
        // 페이징: 최신순으로 정렬
        Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending());
        Page<NewMoim> moimPage = moimRepository.findAll(pageable);

        // 엔티티 -> DTO 변환
        Page<NewMoimDto> dtoPage = moimPage.map(moim -> {
            NewMoimDto dto = new NewMoimDto(moim);
            String cityKoreanName = moim.getCity() != null ? moim.getCity().getKoreanName() : "미지정";
            System.out.println("City Korean Name: " + cityKoreanName);
            return dto;
        });

        model.addAttribute("moims", dtoPage.getContent());
        model.addAttribute("currentPage", dtoPage.getNumber());
        model.addAttribute("totalPages", dtoPage.getTotalPages());
        model.addAttribute("totalItems", dtoPage.getTotalElements());
        model.addAttribute("pageSize", size);

        return "listMoim";
    }

    @GetMapping("/moim/create")
    public String showCreateForm() {
        return "createMoim";
    }

    @PostMapping("/moim/create")
    public String createMoim(@ModelAttribute NewMoimDto dto, BindingResult result) throws IOException {
        if (result.hasErrors()) {
            return "moim/create";
        }

        System.out.println("Raw Images from DTO: " + dto.getImages());
        String imagesJson = dto.getImages() != null && !dto.getImages().isEmpty() ? dto.getImages().get(0) : "[]";
        ObjectMapper mapper = new ObjectMapper();
        List<String> imagePaths;
        try {
            imagePaths = mapper.readValue(imagesJson != null ? imagesJson : "[]",
                    mapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (JsonProcessingException e) {
            System.err.println("JSON Parsing Error: " + e.getMessage());
            imagePaths = new ArrayList<>();
        }
        System.out.println("Parsed Image Paths: " + imagePaths);


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

//        return "redirect:/moim/" + newMoim.getId();
        return "redirect:/moim/list";
    }


    @GetMapping("/moim/districts")
    @ResponseBody
    public ResponseEntity<List<DistrictDto>> getDistricts(@RequestParam(value = "city") String city) {
        System.out.println("Received city: " + city);
        try {
            City cityEnum = City.valueOf(city);
            List<District> districts = districtRepository.findByCity(cityEnum);
            List<DistrictDto> response = districts.stream()
                    .map(d -> new DistrictDto(d.getId(), d.getName()))
                    .collect(Collectors.toList());

            return ResponseEntity.ok(response); // ✅ 명확한 JSON 응답
        } catch (Exception e) {
            System.err.println("Error in getDistricts: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Collections.emptyList());
        }
    }

    private String saveImage(MultipartFile file) {
        String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
        String uploadDir = "uploads"; // 프로젝트 루트/uploads
        Path uploadPath = Paths.get(System.getProperty("user.dir"), uploadDir);
        Path filePath = uploadPath.resolve(fileName);
        try {
            Files.createDirectories(uploadPath);
            file.transferTo(filePath.toFile());
            System.out.println("Saved to: " + filePath.toAbsolutePath());
            System.out.println("File Exists: " + Files.exists(filePath));
            return "/uploads/" + fileName;
        } catch (IOException e) {
            throw new RuntimeException("Failed to save image", e);
        }
    }

    // 개별 이미지 업로드 엔드포인트
    @PostMapping("/moim/upload-image")
    @ResponseBody
    public Map<String, String> uploadImage(@RequestParam("imageFile") MultipartFile file) {
        String path = saveImage(file);
        Map<String, String> response = new HashMap<>();
        response.put("path", path);
        return response;
    }

    // Delete
    @PostMapping("/moim/list/{id}/delete")
    public String deleteMoim(@PathVariable Long id) {
        moimRepository.deleteById(id);
        return "redirect:/moim/list";
    }
}