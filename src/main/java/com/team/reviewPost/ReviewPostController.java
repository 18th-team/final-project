package com.team.reviewPost;

import com.team.moim.service.ClubService;
import com.team.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;
import java.util.List;

@RequestMapping("/review")
@RequiredArgsConstructor
@Controller
public class ReviewPostController {

    private final ReviewPostService reviewPostService;
    private final UserService userService;
    private final ClubService clubService;

    @GetMapping("/list")
    public String reviewList(@RequestParam(value="keyword", required = false) String keyword, Model model, Principal principal){
        int offset = 0;
        int limit = 4;

        // List<ReviewPost> reviewList = (keyword != null && !keyword.isEmpty()) ?



        return "review_list";

        /*
        List<ReviewPost> reviews = reviewPostService.findAll();
        model.addAttribute("reviewList", reviews);
        return "review/list";

         */
    }
}
