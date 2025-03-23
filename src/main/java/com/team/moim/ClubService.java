package com.team.moim;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ClubService {
    private final ClubRepository clubRepository;

    public ClubService(ClubRepository clubRepository) {
        this.clubRepository = clubRepository;
    }

    public Club saveClub(ClubDTO clubDto) {
        Club club = new Club();
        club=buildClubFromDto(clubDto);
        return clubRepository.save(club);
    }

    public void saveImageByInputName(MultipartFile file, String inputName, ClubDTO clubDto) throws IOException {
        if (file == null || file.isEmpty()) return;

        String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
        Path uploadPath = Paths.get("uploads");
        Path filePath = uploadPath.resolve(fileName);
        Files.createDirectories(uploadPath);
        file.transferTo(filePath.toFile());
        String path = "/uploads/" + fileName;

        // inputName에 따라 DTO에 경로 저장
        switch (inputName) {
            case "images01":
                clubDto.setImages01(path);
                break;
            case "images02":
                clubDto.setImages02(path);
                break;
            case "images03":
                clubDto.setImages03(path);
                break;
            default:
                throw new IllegalArgumentException("Unknown input name: " + inputName);
        }
    }

    private Club buildClubFromDto(ClubDTO clubDto) {
        return Club.builder()
                .title(clubDto.getTitle())
                .content(clubDto.getContent())
                .images01(clubDto.getImages01())
                .images02(clubDto.getImages02())
                .images03(clubDto.getImages03())
                .city(clubDto.getCity())
                .district(clubDto.getDistrict())
                .ageRestriction(clubDto.getAgeRestriction())
                .theme(clubDto.getTheme())
                .hostId(clubDto.getHostId())
                .hostName(clubDto.getHostName())
                .build();
    }

}