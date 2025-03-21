package com.team.moim;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/clubs")
public class ClubController {

    private final ClubService clubService;

    public ClubController(ClubService clubService) {
        this.clubService = clubService;
    }

    // 📌 모든 모임 목록 페이지
    @GetMapping
    public String getAllClubs(Model model) {
        List<ClubDTO> clubs = clubService.getAllClubs();
        model.addAttribute("clubs", clubs);
        return "club/list";  // 타임리프 템플릿으로 이동 (club/list.html)
    }


//    // 📌 특정 모임 보기
//    @GetMapping("/{id}")
//    public String getClub(@PathVariable Long id, Model model) {
//        ClubDTO club = clubService.getClubById(id);
//        model.addAttribute("club", club);
//        return "club/detail";  // 타임리프 템플릿으로 이동 (club/detail.html)
//    }

    // 📌 모임 생성 폼
    @GetMapping("/create")
    public String showCreateForm(Model model) {
        model.addAttribute("club", new ClubDTO());
        return "club/create";  // 타임리프 템플릿으로 이동 (club/create.html)
    }

    // 📌 모임 생성 처리
    @PostMapping("/create")
    public String createClub(@ModelAttribute ClubDTO clubDTO) {
        clubService.createClub(clubDTO);
        return "redirect:/clubs";  // 모임 목록 페이지로 리다이렉트
    }

    // 모임 참여 인원 수 업데이트
    @PostMapping("/updateParticipants")
    public String updateParticipants(@ModelAttribute ClubDTO clubDTO) {
        clubService.updateCurrentParticipants(clubDTO);
        return "redirect:/club/list";  // 모임 목록으로 리디렉션
    }

    // 📌 모임 참여 처리
    @GetMapping("/participate/{id}")
    public String participateInClub(@PathVariable Long id) {
        clubService.participateInClub(id);
        return "redirect:/clubs/" + id;  // 참여 후 모임 상세 페이지로 리다이렉트
    }

    // 모임 참여하기
    @PostMapping("/participate/{id}")
    public String participateClub(@PathVariable Long id) {
        clubService.participateInClub(id);
        return "redirect:/clubs/list";  // 참여 후 모임 목록으로 리다이렉트
    }
}