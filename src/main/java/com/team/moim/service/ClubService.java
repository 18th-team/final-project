package com.team.moim.service;

import com.team.moim.ClubDTO;
import com.team.moim.entity.Club;
import com.team.moim.repository.ClubRepository;
import com.team.user.SiteUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

    //1. CRUD 생성
    public void save(ClubDTO clubDTO, SiteUser host) {
        Club clubEntity = Club.toSaveEntity(clubDTO,host);
        clubRepository.save(clubEntity);
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
    public ClubDTO update(ClubDTO clubDTO,SiteUser host) {
        //entity로 변환하는 작업
        Club clubEntity = Club.toUpdateEntity(clubDTO,host);
        clubRepository.save(clubEntity);
        return findById(clubDTO.getId());
    }

    //4. CRUD 삭제
    public void delete(Long id) {
        clubRepository.deleteById(id);
    }
}

