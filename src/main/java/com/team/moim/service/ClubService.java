package com.team.moim.service;

import com.team.moim.ClubDTO;
import com.team.moim.entity.Club;
import com.team.moim.entity.ClubFileEntity;
import com.team.moim.entity.Keyword;
import com.team.moim.repository.ClubFileRepository;
import com.team.moim.repository.ClubRepository;
import com.team.moim.repository.KeywordRepository; // 추가
import com.team.user.SiteUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ClubService {
    private final ClubRepository clubRepository;
    private final ClubFileRepository clubFileRepository;
    private final KeywordRepository keywordRepository; // 의존성 추가

    // 1. 클럽 저장
    @Transactional
    public void save(ClubDTO clubDTO, SiteUser host) throws IOException {
        // theme을 Keyword로 변환
        Set<Keyword> keywords = new HashSet<>();
        if (clubDTO.getSelectedTheme() != null && !clubDTO.getSelectedTheme().isEmpty()) {
            Keyword keyword = keywordRepository.findByName(clubDTO.getSelectedTheme())
                    .orElseGet(() -> keywordRepository.save(new Keyword(null, clubDTO.getSelectedTheme())));
            keywords.add(keyword);
        }

        // 첨부파일 여부에 따라 로직 분리
        if (clubDTO.getClubFile() == null || clubDTO.getClubFile().isEmpty()) {
            Club clubEntity = Club.toSaveEntity(clubDTO, host, keywords);
            clubRepository.save(clubEntity);
        } else {
            Club clubEntity = Club.toSaveFileEntity(clubDTO, host, keywords);
            Long saveId = clubRepository.save(clubEntity).getId();
            Club club = clubRepository.findById(saveId).get();

            // 파일 처리
            for (MultipartFile clubFile : clubDTO.getClubFile()) {
                if (!clubFile.isEmpty()) {
                    String originalFilename = clubFile.getOriginalFilename();
                    String storedFilename = UUID.randomUUID().toString() + "_" + originalFilename;
                    String savePath = "C:/springBoot_img/" + storedFilename;
                    clubFile.transferTo(new File(savePath));
                    ClubFileEntity clubFileEntity = ClubFileEntity.toClubFileEntity(club, originalFilename, storedFilename);
                    clubFileRepository.save(clubFileEntity);
                }
            }
        }
    }

    // 2-1. 전체 목록 조회
    @Transactional(readOnly = true)
    public List<ClubDTO> findAll() {
        List<Club> clubEntityList = clubRepository.findAll();
        List<ClubDTO> clubDTOList = new ArrayList<>();
        for (Club clubEntity : clubEntityList) {
            clubDTOList.add(ClubDTO.toDTO(clubEntity));
        }
        return clubDTOList;
    }

    // 2-2. ID별 조회 (상세보기)
    @Transactional
    public ClubDTO findById(Long id) {
        Optional<Club> optionalClub = clubRepository.findById(id);
        if (optionalClub.isPresent()) {
            Club club = optionalClub.get();
            return ClubDTO.toDTO(club);
        } else {
            return null;
        }
    }

    // 3. 업데이트
    @Transactional
    public ClubDTO update(ClubDTO clubDTO, SiteUser host) throws IOException {
        Club clubEntity = clubRepository.findById(clubDTO.getId())
                .orElseThrow(() -> new IllegalArgumentException("Club not found"));

        // theme을 Keyword로 변환
        Set<Keyword> keywords = new HashSet<>();
        if (clubDTO.getSelectedTheme() != null && !clubDTO.getSelectedTheme().isEmpty()) {
            Keyword keyword = keywordRepository.findByName(clubDTO.getSelectedTheme())
                    .orElseGet(() -> keywordRepository.save(new Keyword(null, clubDTO.getSelectedTheme())));
            keywords.add(keyword);
        }

        // 새 파일 처리
        boolean hasNewFiles = clubDTO.getClubFile() != null && !clubDTO.getClubFile().stream().allMatch(MultipartFile::isEmpty);
        if (hasNewFiles) {
            // 기존 파일 삭제
            if (clubEntity.getFileAttached() == 1) {
                List<ClubFileEntity> existingFiles = clubFileRepository.findByClub(clubEntity);
                for (ClubFileEntity file : existingFiles) {
                    File storedFile = new File("C:/springBoot_img/" + file.getStoredFileName());
                    if (storedFile.exists()) storedFile.delete();
                    clubFileRepository.delete(file);
                }
                clubEntity.getClubFileEntityList().clear(); // 리스트 비우기
            }
            // 새 파일 저장
            for (MultipartFile clubFile : clubDTO.getClubFile()) {
                if (!clubFile.isEmpty()) {
                    String originalFilename = clubFile.getOriginalFilename();
                    String storedFilename = UUID.randomUUID().toString() + "_" + originalFilename;
                    String savePath = "C:/springBoot_img/" + storedFilename;
                    clubFile.transferTo(new File(savePath));
                    ClubFileEntity clubFileEntity = ClubFileEntity.toClubFileEntity(clubEntity, originalFilename, storedFilename);
                    clubEntity.getClubFileEntityList().add(clubFileEntity); // 새 파일 추가
                }
            }
            clubEntity.setFileAttached(1);
        } else {
            // 새 파일이 없으면 기존 clubFileEntityList와 fileAttached 유지
            // toUpdateFileEntity에서 이미 처리됨
        }

        // 엔티티 업데이트
        Club updatedClub = Club.toUpdateFileEntity(clubDTO, host, clubEntity, keywords);
        clubRepository.save(updatedClub);

        return findById(clubDTO.getId());
    }

    // 4. 삭제
    @Transactional
    public void delete(Long id) {
        Club club = clubRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Club not found"));
        if (club.getFileAttached() == 1) {
            List<ClubFileEntity> files = clubFileRepository.findByClub(club);
            for (ClubFileEntity file : files) {
                File storedFile = new File("C:/springBoot_img/" + file.getStoredFileName());
                if (storedFile.exists()) storedFile.delete();
                clubFileRepository.delete(file);
            }
        }
        clubRepository.deleteById(id);
    }
}