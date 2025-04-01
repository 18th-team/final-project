package com.team.moim;

import com.team.moim.entity.Club;
import com.team.moim.entity.Keyword;
import com.team.moim.repository.ClubRepository;
import com.team.moim.repository.KeywordRepository;
import com.team.moim.service.ClubService;
import com.team.reviewPost.ReviewPost;
import com.team.reviewPost.ReviewPostRepository;
import com.team.user.CustomSecurityUserDetails;
import com.team.user.SiteUser;
import com.team.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLDecoder;
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
    private final ReviewPostRepository reviewPostRepository;
    private final ClubRepository clubRepository;
private final KeywordRepository keywordRepository;

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
        return "club/list";
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
        return "club/list";
    }


    //상세보기 (사용자정보 저장하기)
    @GetMapping("/{id}")
    public String findById(@PathVariable Long id, Model model, Authentication authentication) {
        if (id == null) {
            return "redirect:/clubs";
        }
        ClubDTO clubDTO = clubService.findById(id);
        model.addAttribute("clubDTO", clubDTO);

        //note  현재 로그인한 사용자 ID 추가
        if (authentication != null && authentication.isAuthenticated()) {
            CustomSecurityUserDetails userDetails = (CustomSecurityUserDetails) authentication.getPrincipal();
            model.addAttribute("currentUserId", userDetails.getSiteUser().getId());
        } else {
            model.addAttribute("currentUserId", null);
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

        List<ReviewPost> reviews = reviewPostRepository.findByClub(club);
        for (ReviewPost review : reviews) {
            reviewPostRepository.delete(review);
        }

        clubService.delete(id);
        return "redirect:/clubs";
    }





}