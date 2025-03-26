package com.team.moim.service;

import com.team.moim.ClubDTO;
import com.team.moim.entity.Club;
import com.team.moim.entity.ClubFileEntity;
import com.team.moim.repository.ClubFileRepository;
import com.team.moim.repository.ClubRepository;
import com.team.user.SiteUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/*
 * todo
 *  DTO->Entity (Entity클래스)
 *  (사용자가 입력한 값이 DTO에 저장되어있으니 -> 리포지토리로 Entity로 저장해야함)
 *  Entity -> DTO (DTO클래스)
 *  (컨트롤러에서 넘겨 받을때엔 DTO로 받고)
 * */
@Service
@RequiredArgsConstructor
public class ClubService {
    private final ClubRepository clubRepository;
    private final ClubFileRepository clubFileRepository;

    //1. CRUD 생성
    public void save(ClubDTO clubDTO, SiteUser host) throws IOException {
        //note 첨부파일 여부에 따라 로직 분리
        if (clubDTO.getClubFile().isEmpty()) {
            Club clubEntity = Club.toSaveEntity(clubDTO, host);
            clubRepository.save(clubEntity);

        } else {
            //note 부모엔티티에서 자식엔티티꺼내오는거 먼저.
            Club clubEntity = Club.toSaveFileEntity(clubDTO, host); //엔티티로 변환해서 저장
            Long saveId = clubRepository.save(clubEntity).getId(); //아이디값 얻어오기
            Club club = clubRepository.findById(saveId).get(); //부모엔티티에 DB로 부터 가져와

            //note 파일이 여러개일때? 반복문 돌리기
            for (MultipartFile clubFile : clubDTO.getClubFile()) {
                String originalFilename = clubFile.getOriginalFilename();
                String storedFilename = UUID.randomUUID().toString() + "_" + originalFilename;
                String savePath = "C:/springBoot_img/" + storedFilename;
                clubFile.transferTo(new File(savePath));
                ClubFileEntity clubFileEntity = ClubFileEntity.toClubFileEntity(club, originalFilename, storedFilename);
                clubFileRepository.save(clubFileEntity);
            }
        }
    }

    //2-1. CRUD 전체 목록 조회
    @Transactional(readOnly = true)
    public List<ClubDTO> findAll() {
        List<Club> clubEntityList = clubRepository.findAll();
        List<ClubDTO> clubDTOList = new ArrayList<>();
        for (Club clubEntity : clubEntityList) {
            clubDTOList.add(ClubDTO.toDTO(clubEntity));
        }
        return clubDTOList;

    }

    //2-1. ID별로 조회(상세보기)
    @Transactional
    public ClubDTO findById(Long id) {
        Optional<Club> optionalClub = clubRepository.findById(id);
        if (optionalClub.isPresent()) {
            Club club = optionalClub.get();
            ClubDTO clubDTO = ClubDTO.toDTO(club);
            return clubDTO;
        } else {
            return null;
        }
    }


    //3. CRUD 업데이트
    @Transactional
    public ClubDTO update(ClubDTO clubDTO, SiteUser host) throws IOException {
        Club clubEntity = clubRepository.findById(clubDTO.getId())
                .orElseThrow(() -> new IllegalArgumentException("Club not found"));
        clubEntity = Club.toUpdateFileEntity(clubDTO, host, clubEntity);
        clubRepository.save(clubEntity);

        if (clubDTO.getClubFile() != null && !clubDTO.getClubFile().stream().allMatch(MultipartFile::isEmpty)) {
            if (clubEntity.getFileAttached() == 1) {
                List<ClubFileEntity> existingFiles = clubFileRepository.findByClub(clubEntity);
                for (ClubFileEntity file : existingFiles) {
                    File storedFile = new File("C:/springBoot_img/" + file.getStoredFileName());
                    if (storedFile.exists()) storedFile.delete();
                    clubFileRepository.delete(file);
                }
            }
            for (MultipartFile clubFile : clubDTO.getClubFile()) {
                if (!clubFile.isEmpty()) {
                    String originalFilename = clubFile.getOriginalFilename();
                    String storedFilename = UUID.randomUUID().toString() + "_" + originalFilename;
                    String savePath = "C:/springBoot_img/" + storedFilename;
                    clubFile.transferTo(new File(savePath));
                    ClubFileEntity clubFileEntity = ClubFileEntity.toClubFileEntity(clubEntity, originalFilename, storedFilename);
                    clubFileRepository.save(clubFileEntity);
                }
            }
            clubEntity.setFileAttached(1);
            clubRepository.save(clubEntity);
        }

        return findById(clubDTO.getId());
    }

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

