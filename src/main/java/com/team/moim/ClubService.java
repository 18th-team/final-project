package com.team.moim;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ClubService {
    private final ClubRepository clubRepository;

    public ClubService(ClubRepository clubRepository) {
        this.clubRepository = clubRepository;
    }

    //  📌모임 참여 처리
    @Transactional
    public void updateCurrentParticipants(ClubDTO clubDTO) {
        // clubDTO에서 id로 Club을 찾아오기
        Club club = clubRepository.findById(clubDTO.getId())
                .orElseThrow(() -> new RuntimeException("모임을 찾을 수 없습니다."));

        // 참여 인원 수 업데이트
        club.setCurrentParticipants(clubDTO.getCurrentParticipants());

        // DB에 저장
        clubRepository.save(club);
    }

    // 📌 모임 등록 (DTO → Entity 변환 후 저장)
    @Transactional
    public ClubDTO createClub(ClubDTO clubDTO) {
        Club club = new Club();
        club.setTitle(clubDTO.getTitle());
        club.setDescription(clubDTO.getDescription());
        club.setHostId(clubDTO.getHostId());
        club.setHostNickname(clubDTO.getHostNickname());
        club.setStatus("모집 중");
        club.setMinParticipants(clubDTO.getMinParticipants());
        club.setMaxParticipants(clubDTO.getMaxParticipants());
        club.setCurrentParticipants(0);

        Club savedClub = clubRepository.save(club);
        return convertToDTO(savedClub);
    }

    // 📌 특정 모임 조회
    public ClubDTO getClubById(Long id) {
        Club club = clubRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Club not found"));
        return convertToDTO(club);
    }

    // 📌 모든 모임 조회
    public List<ClubDTO> getAllClubs() {
        List<Club> clubs = clubRepository.findAll();
        return clubs.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    // 📌 모집 중인 모임 조회
    public List<ClubDTO> getActiveClubs() {
        List<Club> clubs = clubRepository.findByStatus("모집 중");
        return clubs.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    // 📌 DTO 변환 메서드 (Entity → DTO)
    private ClubDTO convertToDTO(Club club) {
        ClubDTO dto = new ClubDTO();
        dto.setId(club.getId());
        dto.setTitle(club.getTitle());
        dto.setDescription(club.getDescription());
        dto.setHostId(club.getHostId());
        dto.setHostNickname(club.getHostNickname());
        dto.setCreatedAt(club.getCreatedAt());
        dto.setUpdatedAt(club.getUpdatedAt());
        dto.setStatus(club.getStatus());
        dto.setCurrentParticipants(club.getCurrentParticipants());
        dto.setMinParticipants(club.getMinParticipants());
        dto.setMaxParticipants(club.getMaxParticipants());
        return dto;
    }


    @Transactional
    public void participateInClub(Long clubId) {
        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new IllegalArgumentException("모임을 찾을 수 없습니다."));

        // 최대 인원에 도달했는지 확인
        if (club.getCurrentParticipants() >= club.getMaxParticipants()) {
            club.setIsFull(true);  // 모임이 마감됨
        } else {
            club.setCurrentParticipants(club.getCurrentParticipants() + 1);  // 참여 인원 증가
        }

        clubRepository.save(club);  // 변경된 내용 저장
    }
}