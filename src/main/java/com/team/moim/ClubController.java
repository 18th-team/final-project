package com.team.moim;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.moim.entity.Club;
import com.team.moim.entity.Keyword;
import com.team.moim.repository.ClubRepository;
import com.team.moim.repository.KeywordRepository;
import com.team.moim.service.ClubService;
import com.team.user.CustomSecurityUserDetails;
import com.team.user.SiteUser;
import com.team.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.repository.query.Param;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/clubs")
@RequiredArgsConstructor
public class ClubController {
    private final ClubService clubService;
    private final UserService userService;
    private final ClubRepository clubRepository;
    private final KeywordRepository keywordRepository;

    //지도API
    @Autowired
    private RestTemplate restTemplate; // HTTP 요청용

    private static final String GEOCODING_API_URL = "https://naveropenapi.apigw.ntruss.com/map-geocode/v2/geocode?query=";
    private static final String CLIENT_ID = "nlpedkwvft";
    private static final String CLIENT_SECRET = "3cKYBuFWFl9j1tmZiJQzu5gzzZjgDTIX5XkoP60D";




    // ✅ 중복 코드 줄이기 ->
    @ModelAttribute("keywordList")
    public List<Keyword> populateKeywords(@RequestParam(value = "id", required = false) String id, HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri.equals("/clubs") || uri.startsWith("/clubs/category") || uri.equals("/")) {
            return keywordRepository.findAll();
        }
        return null; // /clubs/10 같은 경로에서는 키워드 목록 안 보냄
    }


    @GetMapping("/create")
    public String createForm(Model model) {
        model.addAttribute("clubDTO", new ClubDTO());
        return "club/create";
    }

    @PostMapping("/insert")
    public String createClub(@ModelAttribute ClubDTO clubDTO, Authentication authentication) throws IOException {

        CustomSecurityUserDetails userDetails = (CustomSecurityUserDetails) authentication.getPrincipal();
        SiteUser host = userDetails.getSiteUser();
        clubService.save(clubDTO, host);
        return "redirect:/clubs";
    }

    // ✅ 전체 클럽 리스트 조회
    @GetMapping()
    public String findAllClubs(Model model) {
        List<Club> clubs = clubRepository.findAll();
        List<ClubDTO> clubDTOList = clubs.stream()
                .map(ClubDTO::toDTO)
                .collect(Collectors.toList());
        model.addAttribute("clubList", clubDTOList);
        System.out.println("All clubs: " + clubDTOList.size()); // 디버깅
        return "club/list";
    }

    // 검색 처리
    @GetMapping("/search")
    public String searchClubs(@RequestParam("query") String query, Model model) {
        List<ClubDTO> clubDTOList = clubService.searchClubs(query);
        model.addAttribute("clubList", clubDTOList);
        return "club/list"; // list.html로 렌더링
    }

    //카테고리 클랙시 -> 해당 카테고리와 연관된 클럽목록 불러오기
    // 키워드 ID로 클럽 목록 조회
    @GetMapping("/category/{id}")
    public String getClubsByKeywordId(@PathVariable("id") Long keywordId, Model model) {
        // 키워드 ID로 클럽 목록 조회
        List<Club> clubs = clubRepository.findByKeywords_Id(keywordId);
        List<ClubDTO> clubDTOList = clubs.stream()
                .map(ClubDTO::toDTO)
                .collect(Collectors.toList());
        model.addAttribute("clubList", clubDTOList);
        System.out.println("Keyword ID: " + keywordId + ", Clubs found: " + clubDTOList.size()); // 디버깅
        return "club/list";
    }


    //상세보기 (사용자정보 저장하기)
    @GetMapping("/{id}")
    public String getClubDetail(@PathVariable("id") Long id, Model model) {
        ClubDTO clubDTO = clubService.getClubDetail(id);
        model.addAttribute("clubDTO", clubDTO);

        // Geocoding API 호출
        try {
            String fullAddress = clubDTO.getFullAddress(); // "수원시 장안구"
            String url = GEOCODING_API_URL + URLEncoder.encode(fullAddress, StandardCharsets.UTF_8.name());
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-NCP-APIGW-API-KEY-ID", CLIENT_ID);
            headers.set("X-NCP-APIGW-API-KEY", CLIENT_SECRET);
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            String jsonResponse = response.getBody();
            System.out.println("API Response: " + jsonResponse); // 디버깅

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(jsonResponse);
            JsonNode addresses = root.path("addresses");
            if (addresses.isEmpty()) {
                System.out.println("No addresses found for: " + fullAddress);
                model.addAttribute("latitude", 37.5665);
                model.addAttribute("longitude", 126.9780);
            } else {
                JsonNode address = addresses.get(0);
                double latitude = address.path("y").asDouble();
                double longitude = address.path("x").asDouble();
                model.addAttribute("latitude", latitude);
                model.addAttribute("longitude", longitude);
            }
        } catch (Exception e) {
            System.out.println("Geocoding API Error: " + e.getMessage());
            model.addAttribute("latitude", 37.5665);
            model.addAttribute("longitude", 126.9780);
        }

        return "club/detail";
    }

    //수정하기
    //수정 컨트롤러
    @GetMapping("/update/{id}")
    public String updateform(@PathVariable Long id, Model model) {
        ClubDTO clubDTO = clubService.findById(id);
        model.addAttribute("clubUpdate", clubDTO);
        return "club/update";
    }

    @PostMapping("/update")
    public String update(@ModelAttribute ClubDTO clubDTO, Model model, Authentication authentication) throws IOException {
        CustomSecurityUserDetails userDetails = (CustomSecurityUserDetails) authentication.getPrincipal();
        SiteUser host = userDetails.getSiteUser();
        ClubDTO club = clubService.update(clubDTO, host);
        model.addAttribute("clubUpdate", club);
        return "redirect:/clubs/" + club.getId();
    }

    //삭제하기
    @Transactional
    @PostMapping("/delete/{id}")
    public String delete(@PathVariable Long id) {
        Club club = clubRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Club not found"));

        clubService.delete(id);
        return "redirect:/clubs";
    }


    //해당 클럽 참여하기
    @PostMapping("/join/{clubId}")
    public String joinClub(@PathVariable("clubId") Long clubId,
                           @AuthenticationPrincipal CustomSecurityUserDetails user,
                           RedirectAttributes redirectAttributes) {
        if (user == null) {
            redirectAttributes.addFlashAttribute("error", "로그인이 필요합니다!");
            return "redirect:/login";
        }
        // 서비스 호출
        boolean isJoined = clubService.joinClub(clubId, user.getUsername()); // email 반환
        if (isJoined) {
            redirectAttributes.addFlashAttribute("message", "참여완료!");
        } else {
            redirectAttributes.addFlashAttribute("message", "이미 참여하셨습니다 😁");
        }
        return "redirect:/clubs/" + clubId;
    }

    //    //클럽 취소하기
    @PostMapping("/leave/{clubId}")
    public String leaveClub(@PathVariable("clubId") Long clubId, @AuthenticationPrincipal CustomSecurityUserDetails user, RedirectAttributes redirectAttributes) {
        if (user == null) {
            redirectAttributes.addFlashAttribute("error", "로그인이 필요합니다!");
            return "redirect:/login";
        }
        boolean isLeft = clubService.leaveClub(clubId, user.getUsername());
        if (isLeft) {
            redirectAttributes.addFlashAttribute("message","참여 취소 되었습니다 ! ");
        }
        else {redirectAttributes.addFlashAttribute("error","참여하지 않은 클럽입니다.");
        }
        return "redirect:/clubs/" + clubId;
    }



}