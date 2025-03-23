package com.team.moim;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Controller
@RequestMapping("/clubs")
public class ClubController {
    private final ClubService clubService;

    public ClubController(ClubService clubService) {
        this.clubService = clubService;
    }
    @GetMapping("/create")
    public String create() {
        return "club/create";
    }

    @PostMapping("/insert")
    public String insert(@ModelAttribute ClubDTO clubDto, MultipartFile multipartFile
    ,@RequestParam(value = "images01", required = false) MultipartFile images01,
                         @RequestParam(value = "images02", required = false) MultipartFile images02,
                         @RequestParam(value = "images03", required = false) MultipartFile images03) throws IOException {
        //사용자한테 입력받은 값을 -> 데이터베이스에 저장해야함.
//        clubService.saveImageByInputName(images01, "images01", clubDto);
//        clubService.saveImageByInputName(images02, "images02", clubDto);
//        clubService.saveImageByInputName(images03, "images03", clubDto);
        clubService.saveClub(clubDto);
        return "redirect:/club/list";
    }


    @GetMapping("/createOneDay")
    public String createOneDay() {
        return "club/createOneDay";
    }
}