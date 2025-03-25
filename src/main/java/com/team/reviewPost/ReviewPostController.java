package com.team.reviewPost;

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

    @GetMapping("/list")
    public String reviewList(@RequestParam(value="keyword", required = false) String keyword, Model model, Principal principal){
        int offset = 0;
        int limit = 4;

        // List<ReviewPost> reviewList = (keyword != null && !keyword.isEmpty()) ?


        return "review_list";
    }
}
