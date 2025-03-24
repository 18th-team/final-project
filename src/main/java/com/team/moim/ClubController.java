package com.team.moim;

import com.team.moim.service.ClubService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@Controller
@RequestMapping("/clubs")
@RequiredArgsConstructor
public class ClubController {
    private final ClubService clubService;

    @GetMapping("/create")
    public String create() {
        return "club/create";
    }

    @PostMapping("/insert")
    public String insert(@ModelAttribute ClubDTO clubDTO) {
        System.out.println("clubDTO: " + clubDTO);
        clubService.save(clubDTO);
        return "redirect:/clubs";

    }

    @GetMapping()
    //DB에서 전체게시물을 가지고 와서, list.html에 보여줌
    public String findAll(Model model) {
        List<ClubDTO> clubDTOList = clubService.findAll();
        model.addAttribute("clubDTOList", clubDTOList);
        return "club/list"; //list.html로 흘러간다.
    }

    //상세보기
    @GetMapping("/{id}")
    public String findById(@PathVariable Long id, Model model) {
        ClubDTO clubDTO = clubService.findById(id);
        model.addAttribute("clubDTO", clubDTO);
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
    public String update(@ModelAttribute ClubDTO clubDTO, Model model) {
        ClubDTO club = clubService.update(clubDTO);
        model.addAttribute("clubUpdate", club);
        return "club/detail";
    }

    //삭제하기
    @PostMapping("/delete/{id}")
    public String delete(@PathVariable Long id) {
        clubService.delete(id);
        return "redirect:/clubs";
    }

}