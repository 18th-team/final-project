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


@Controller
@RequestMapping("/clubs")
@RequiredArgsConstructor
public class ClubController {
    private final ClubService clubService;
    private final UserService userService;
    private final ReviewPostRepository reviewPostRepository;
    private final ClubRepository clubRepository;
private final KeywordRepository keywordRepository;

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

    @GetMapping()
    //DB에서 전체게시물을 가지고 와서, list.html에 보여줌
    public String findAll(Model model) {
        List<ClubDTO> clubDTOList = clubService.findAll();
        model.addAttribute("clubDTOList", clubDTOList);
        // 키워드 목록 조회
        List<Keyword> keywordList = keywordRepository.findAll();
        model.addAttribute("keywordList", keywordList);
        return "club/list"; //list.html로 흘러간다.
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

    //카테고리 클랙시 -> 해당 카테고리와 연관된 클럽목록 불러오기
    @GetMapping("/category/{categoryName}")
    public String findByCategory(@PathVariable String categoryName, Model model) {
        List<ClubDTO> clubDTOList = clubService.findByCategory(categoryName);
        // 키워드 목록 조회
        List<Keyword> keywordList = keywordRepository.findAll();
        model.addAttribute("keywordList", keywordList);
        model.addAttribute("clubDTOList", clubDTOList);
        return "club/list"; // list.html에 전달
    }



}