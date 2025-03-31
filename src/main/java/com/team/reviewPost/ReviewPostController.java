package com.team.reviewPost;

import com.team.FileService;
import com.team.feedPost.FeedPost;
import com.team.moim.ClubDTO;
import com.team.moim.entity.Club;
import com.team.moim.service.ClubService;
import com.team.user.SiteUser;
import com.team.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.List;

@RequestMapping("/review")
@RequiredArgsConstructor
@Controller
public class ReviewPostController {

    private final ReviewPostService reviewPostService;
    private final UserService userService;
    private final ClubService clubService;
    private final FileService fileService;

    @GetMapping("/list")
    public String reviewList(@RequestParam(value="keyword", required = false) String keyword, Model model, Principal principal){
        int offset = 0;
        int limit = 4;

        List<ReviewPost> reviewList = (keyword != null && !keyword.isEmpty()) ?
                reviewPostService.searchByKeyword(keyword, offset, limit) :
                reviewPostService.findLimited(offset, limit);

        long totalCount = (keyword != null && !keyword.isEmpty()) ?
                reviewPostService.countByKeyword(keyword) :
                reviewPostService.count();

        model.addAttribute("reviewList", reviewList);
        model.addAttribute("hasMore", totalCount > limit); // 더보기 버튼 표시 여부
        model.addAttribute("keyword", keyword); // 검색어 유지용
        model.addAttribute("clubList", clubService.findAll());



        if (principal != null) {
            SiteUser loginUser = userService.getUser(principal.getName());
            model.addAttribute("loginUser", loginUser);
        }

        return "review_list";

    }

    @GetMapping("/more")
    @ResponseBody
    public List<ReviewPost> loadMorePosts(@RequestParam int offset, @RequestParam int limit) {
        return reviewPostService.findLimited(offset, limit);
    }

    @GetMapping("/create")
    public String reviewForm(Model model, RedirectAttributes redirectAttributes) {
        model.addAttribute("reviewForm", new ReviewPostForm());

        List<ClubDTO> clubList = clubService.findAll();
        model.addAttribute("clubList", clubService.findAll());

        if (clubList.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "후기를 작성할 모임이 없습니다!");
            return "redirect:/review/list"; // 클럽 생성 폼 URL로 리다이렉트 (필요에 따라 변경)
        }

        return "review_form";
    }

    @PostMapping("/create")
    public String createReviewPost(@ModelAttribute ReviewPostForm reviewForm, @RequestParam("imageURL") MultipartFile imageFile, Principal principal) {
        SiteUser user = userService.getUser(principal.getName());
        Club club = clubService.getClub(reviewForm.getClubID());

        String imagePath = null;
        if (!imageFile.isEmpty()) {
            imagePath = fileService.saveImage(imageFile);
        }

        reviewPostService.create(
                reviewForm.getTitle(),
                reviewForm.getContent(),
                reviewForm.getTags(),
                club,
                imagePath,
                user
        );

        return "redirect:/review/list";
    }

    @GetMapping("/vote/{id}")
    public String vote(@PathVariable Integer id, Principal principal) {
        ReviewPost reviewPost = reviewPostService.getReviewPost(id);
        SiteUser siteUser = userService.getUser(principal.getName());

        if (reviewPost.getVoter().contains(siteUser)) {
            reviewPostService.cancelVote(reviewPost, siteUser);
        } else {
            reviewPostService.vote(reviewPost, siteUser);
        }

        return "redirect:/review/list";
    }
}
