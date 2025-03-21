package com.team.feedpost;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.ArrayList;
import java.util.List;

@RequestMapping("/feed")
@RequiredArgsConstructor
@Controller
public class FeedPostController {

    @GetMapping("/create")
    public String feedForm(Model model) {
        model.addAttribute("feedForm", new FeedPostForm());
        return "feed/create";
    }

    @PostMapping("/create")
    public String createFeedPost(FeedPostForm feedForm, Model model) {
        return "test";
    }
}
