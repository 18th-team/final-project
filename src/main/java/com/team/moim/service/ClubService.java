package com.team.moim.service;

import com.team.API.DistanceUtil;
import com.team.chat.*;
import com.team.chat.ChatRoom;
import com.team.chat.ChatRoomService;
import com.team.moim.ClubDTO;
import com.team.moim.entity.Club;
import com.team.moim.entity.ClubFileEntity;
import com.team.moim.entity.Keyword;
import com.team.moim.repository.ClubFileRepository;
import com.team.moim.repository.ClubRepository;
import com.team.moim.repository.KeywordRepository; // 추가
import com.team.user.SiteUser;
import com.team.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClubService {
    private final ClubRepository clubRepository;
    private final ClubFileRepository clubFileRepository;
    private final KeywordRepository keywordRepository; // 의존성 추가
    private final UserRepository userRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final NoticeRepository noticeRepository;
    private final ChatRoomService chatRoomService;
    private final ChatRoomRepository chatRoomRepository;

    // 1. ✅ 클럽 CREATE로직
    // 1. 클럽 저장
    @Transactional
    public Club save(ClubDTO clubDTO, SiteUser host) throws IOException {
        // 키워드
        Set<Keyword> keywords = new HashSet<>();

        if (StringUtils.hasText(clubDTO.getSelectedTheme())) {
            Keyword keyword = keywordRepository.findByName(clubDTO.getSelectedTheme())
                    .orElseGet(() -> keywordRepository.save(new Keyword(null, clubDTO.getSelectedTheme())));
            keywords.add(keyword);
        }

        // 첨부파일 여부에 따라 로직 분리
        Club clubEntity;

        // 호스트를 멤버로 추가
        Set<SiteUser> member = new HashSet<>();

        if (clubDTO.getClubFile() == null || clubDTO.getClubFile().isEmpty()) {
            clubEntity = Club.toSaveEntity(clubDTO, host, keywords,member);
            clubEntity.getMembers().add(host);

        } else {
            clubEntity = Club.toSaveFileEntity(clubDTO, host, keywords,member);
            clubEntity.getMembers().add(host);

            // 파일 처리
            for (MultipartFile clubFile : clubDTO.getClubFile()) {
                if (!clubFile.isEmpty()) {
                    String originalFilename = clubFile.getOriginalFilename();
                    String storedFilename = UUID.randomUUID().toString() + "_" + originalFilename;
                    String directoryPath = "C:/springBoot_img/";
                    String savePath = directoryPath + storedFilename;
                    File directory = new File(directoryPath);
                    if (!directory.exists()) {
                        directory.mkdirs();
                    }
                    clubFile.transferTo(new File(savePath));
                    ClubFileEntity clubFileEntity = ClubFileEntity.toClubFileEntity(clubEntity, originalFilename, storedFilename);
                    clubFileRepository.save(clubFileEntity);
                }
            }
        }
        Club savedClub = clubRepository.save(clubEntity);
        return savedClub;
    }




    // 2-1. 전체 목록 조회
//note 엔티티->DTO stream().map(ClubDTO::toDTO) 필수?
    public List<ClubDTO> findAll() {
        return clubRepository.findAll().stream().map(ClubDTO::toDTO).collect(Collectors.toList());
    }

    // 2-2. ID별 조회 (상세보기)
    @Transactional
    public ClubDTO findById(Long id) {
        Optional<Club> optionalClub = clubRepository.findById(id);
        optionalClub.ifPresent(c -> System.out.println("Found Club: id=" + c.getId() +
                ", location=" + c.getLocation() +
                ", latitude=" + c.getLatitude() +
                ", longitude=" + c.getLongitude()));
        if (optionalClub.isPresent()) {
            Club club = optionalClub.get();
            return ClubDTO.toDTO(club);
        } else {
            return null;
        }
    }

    // 3. 업데이트
    @Transactional
    public ClubDTO update(ClubDTO clubDTO, String location, String locationTitle, Double latitude, Double longitude, SiteUser host) throws IOException {

        Club club = clubRepository.findById(clubDTO.getId())
                .orElseThrow(() -> new IllegalArgumentException("Club not found"));
        // 기존 멤버 유지
        Set<SiteUser> members = club.getMembers();

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
            if (club.getFileAttached() == 1) {
                List<ClubFileEntity> existingFiles = clubFileRepository.findByClub(club);
                for (ClubFileEntity file : existingFiles) {
                    File storedFile = new File("C:/springBoot_img/" + file.getStoredFileName());
                    if (storedFile.exists()) storedFile.delete();
                    clubFileRepository.delete(file);
                }
                club.getClubFileEntityList().clear(); // 리스트 비우기
            }
            // 새 파일 저장
            for (MultipartFile clubFile : clubDTO.getClubFile()) {
                if (!clubFile.isEmpty()) {
                    String originalFilename = clubFile.getOriginalFilename();
                    String storedFilename = UUID.randomUUID().toString() + "_" + originalFilename;
                    String savePath = "C:/springBoot_img/" + storedFilename;
                    clubFile.transferTo(new File(savePath));
                    ClubFileEntity clubFileEntity = ClubFileEntity.toClubFileEntity(club, originalFilename, storedFilename);
                    club.getClubFileEntityList().add(clubFileEntity); // 새 파일 추가
                }
            }
            club.setFileAttached(1);
        }
        // 엔티티 업데이트
        Club updatedClub = Club.toUpdateFileEntity(clubDTO, location, locationTitle, latitude, longitude, host, club, keywords,members);

        //모임 업데이트 시 채팅방 자동업데이트
        ChatRoom chatRoom =  chatRoomService.updateChatRoom(
                updatedClub.getId(),
                updatedClub.getTitle()
        );
        updatedClub.setChatRoom(chatRoom);
        clubRepository.save(updatedClub);
        ClubDTO result = findById(updatedClub.getId());
        result.setMemberCount(updatedClub.getMembers().size());
        return result;
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
        ChatRoom chatRoom = club.getChatRoom();
        if (chatRoom != null) {
            Optional<Notice> noticeToDelete = noticeRepository.findByChatRoom(chatRoom);
            noticeToDelete.ifPresent(noticeRepository::delete);
            chatMessageRepository.deleteByChatRoom(chatRoom);
        }
        clubRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public Club getClub(Long id) {
        return clubRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Club not found for id: " + id));
    }


    //클럽생성시 선택된 단일키워드로 해당 클럽 전체를 저장함.
    public List<Club> getRecommendedClubs(List<String> userKeywords) {
        if (userKeywords == null || userKeywords.isEmpty()) {
            return new ArrayList<>(); // 키워드가 없으면 빈 리스트 반환
        }
        return clubRepository.findByKeywords_NameIn(userKeywords);
    }

    //검색기능
    public List<ClubDTO> searchClubs(String query) {
        List<Club> clubs = clubRepository.findBySearchQuery(query);
        return clubs.stream().map(ClubDTO::toDTO).collect(Collectors.toList());
    }

    //클럽 참여하기
    public boolean joinClub(Long clubId, String userEmail) {
        System.out.println(userEmail);
        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new IllegalArgumentException("Club not found"));
        SiteUser user = userRepository.findByUuid(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        // 이미 가입했는지 체크
        if (club.getMembers().contains(user)) {
            return false; // 중복이면 false 반환
        }

        // 가입 처리
        club.getMembers().add(user);
        user.getClubs().add(club);
        clubRepository.save(club);
        return true; // 성공하면 true 반환
    }


    // 클럽id 상세보기
    public ClubDTO getClubDetail(Long clubId) {
        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new IllegalArgumentException("Club not found"));
        return ClubDTO.toDTO(club);
    }

    // 클럽 참여 취소하기 ( 참여인이면 true, 참여안했으면 false )
    public boolean leaveClub(Long clubId, String userEmail) {
        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new IllegalArgumentException("Club not found"));
        SiteUser user = userRepository.findByUuid(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        System.out.println("Before remove: " + club.getMembers().size());
        // 가입되어 있는지 체크
        if (!club.getMembers().contains(user)) {
            return false; // 가입 안 했으면 false
        }

        // 가입했으면 -> 가입 취소
        club.getMembers().remove(user);
        user.getClubs().remove(club);
        System.out.println("After remove: " + club.getMembers().size());
        clubRepository.save(club);
        return true;
    }

    //5km 이내 클럽 필터링 및 정렬(사용자위치기반)
    public List<ClubDTO> findNearByClubs(double userLat, double userLng) {
        List<Club> allClubs = clubRepository.findAll();
        return allClubs.stream().filter(club -> club.getLatitude() != null && club.getLongitude() != null) // null 필터링
                .map(club -> {
                    double distance = DistanceUtil.calculateDistance(
                            userLat, userLng, club.getLatitude(), club.getLongitude()
                    );
                    ClubDTO dto = ClubDTO.toDTO(club);
                    dto
                            .setDistance(distance);
                    return dto;
                }).filter(dto -> dto.getDistance() <= 5).sorted(Comparator.comparingDouble(ClubDTO::getDistance)).limit(5).collect(Collectors.toList());
    }

    public List<Club> getClubsByUser(SiteUser user) {
        return clubRepository.findByMembersContaining(user);
    }
}