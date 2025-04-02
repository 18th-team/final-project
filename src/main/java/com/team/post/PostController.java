package com.team.post;

import com.team.FileService;
import com.team.moim.entity.Club;
import com.team.moim.service.ClubService;
import com.team.user.SiteUser;
import com.team.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@RequestMapping("/post")
@Controller
public class PostController {

    private final PostService postService;
    private final UserService userService;
    private final ClubService clubService;
    private final FileService fileService;

    // ========================
    // 피드 리스트
    // ========================
    @GetMapping("/feed/list")
    public String feedList(@RequestParam(value = "keyword", required = false) String keyword,
                           Model model, Principal principal) {
        int offset = 0;
        int limit = 4;

        List<Post> postList = (keyword != null && !keyword.isEmpty()) ?
                postService.searchFeedOnly(keyword, offset, limit) :
                postService.findFeedOnly(offset, limit);

        long totalCount = (keyword != null && !keyword.isEmpty()) ?
                postService.countFeedOnlyByKeyword(keyword) :
                postService.countFeedOnly();

        model.addAttribute("postList", postList);
        model.addAttribute("hasMore", totalCount > limit);
        model.addAttribute("keyword", keyword);

        if (principal != null) {
            SiteUser loginUser = userService.getUser(principal.getName());
            model.addAttribute("loginUser", loginUser);
        }

        return "post_feed_list";
    }

    // 더보기 API (피드)
    @GetMapping("/feed/more")
    @ResponseBody
    public List<PostDTO> loadMoreFeed(@RequestParam int offset, @RequestParam int limit) {
        return postService.findFeedOnly(offset, limit).stream()
                .map(PostDTO::from)
                .toList();
    }


    // ========================
    // 후기 리스트
    // ========================
    @GetMapping("/review/list")
    public String reviewList(@RequestParam(value = "keyword", required = false) String keyword,
                             Model model, Principal principal) {
        int offset = 0;
        int limit = 4;

        List<Post> postList = (keyword != null && !keyword.isEmpty()) ?
                postService.searchReviewOnly(keyword, offset, limit) :
                postService.findReviewOnly(offset, limit);

        long totalCount = (keyword != null && !keyword.isEmpty()) ?
                postService.countReviewOnlyByKeyword(keyword) :
                postService.countReviewOnly();

        model.addAttribute("postList", postList);
        model.addAttribute("hasMore", totalCount > limit);
        model.addAttribute("keyword", keyword);
        model.addAttribute("clubList", clubService.findAll());

        if (principal != null) {
            SiteUser loginUser = userService.getUser(principal.getName());
            model.addAttribute("loginUser", loginUser);
        }


        return "post_review_list";
    }

    // 더보기 API (후기)
    @GetMapping("/review/more")
    @ResponseBody
    public List<Post> loadMoreReview(@RequestParam int offset, @RequestParam int limit) {
        return postService.findReviewOnly(offset, limit);
    }

    // ========================
    // 작성 폼
    // ========================
    @GetMapping("/feed/create")
    public String feedForm(Model model) {
        model.addAttribute("postForm", new PostForm());
        model.addAttribute("isReview", false);
        return "post_form";
    }

    @GetMapping("/review/create")
    public String reviewForm(Model model) {
        model.addAttribute("postForm", new PostForm());
        model.addAttribute("isReview", true);
        model.addAttribute("clubList", clubService.findAll());
        return "post_form";
    }

    // 작성 처리
    @PostMapping("/create")
    public String create(@ModelAttribute PostForm form,
                         @RequestParam("imageURL") MultipartFile imageFile,
                         Principal principal) {
        SiteUser user = userService.getUser(principal.getName());
        Club club = (form.getClubID() != null) ? clubService.getClub(form.getClubID()) : null;

        String imagePath = null;
        if (!imageFile.isEmpty()) {
            imagePath = fileService.saveImage(imageFile);
        }

        postService.create(form.getTitle(), form.getContent(), form.getTags(), imagePath, club, user, form.getBoardType());

        return form.getBoardType() == BoardType.REVIEW ? "redirect:/post/review/list" : "redirect:/post/feed/list";
    }

    // ========================
    // 수정
    // ========================
    @GetMapping("/modify/{id}")
    public String modifyForm(@PathVariable Integer id, Principal principal, Model model) {
        Post post = postService.getPost(id);
        SiteUser user = userService.getUser(principal.getName());

        if (!post.getAuthor().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        PostForm form = new PostForm();
        form.setTitle(post.getTitle());
        form.setContent(post.getContent());
        form.setTags(post.getTags());
        if (post.getClub() != null) {
            form.setClubID(post.getClub().getId());
            model.addAttribute("isReview", true);
            model.addAttribute("clubList", clubService.findAll());
        } else {
            model.addAttribute("isReview", false);
        }

        model.addAttribute("postForm", form);
        model.addAttribute("postID", post.getPostID());
        model.addAttribute("existingImage", post.getImageURL());

        return "post_form";
    }

    @PostMapping("/modify/{id}")
    public String modify(@PathVariable Integer id,
                         @ModelAttribute PostForm form,
                         @RequestParam("imageURL") MultipartFile imageFile,
                         @RequestParam(value = "existingImage", required = false) String existingImage,
                         Principal principal) {
        Post post = postService.getPost(id);
        SiteUser user = userService.getUser(principal.getName());

        if (!post.getAuthor().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        Club club = (form.getClubID() != null) ? clubService.getClub(form.getClubID()) : null;

        String imagePath = null;
        if (!imageFile.isEmpty()) {
            imagePath = fileService.saveImage(imageFile);
        } else {
            imagePath = existingImage; // 기존 이미지 유지
        }

        postService.modify(post, form.getTitle(), form.getContent(), form.getTags(), imagePath, club);
        return (club == null) ? "redirect:/post/feed/list" : "redirect:/post/review/list";
    }

    // ========================
    // 삭제
    // ========================
    @GetMapping("/delete/{id}")
    public String delete(@PathVariable Integer id, Principal principal) {
        Post post = postService.getPost(id);
        SiteUser user = userService.getUser(principal.getName());

        if (!post.getAuthor().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        boolean isReview = post.getClub() != null;
        postService.delete(post);
        return isReview ? "redirect:/post/review/list" : "redirect:/post/feed/list";
    }

    // ========================
    // 좋아요
    // ========================
    @GetMapping("/vote/{id}")
    public String vote(@PathVariable Integer id, Principal principal) {
        Post post = postService.getPost(id);
        SiteUser user = userService.getUser(principal.getName());

        if (post.getVoter().contains(user)) {
            postService.cancelVote(post, user);
        } else {
            postService.vote(post, user);
        }

        return post.getClub() == null ? "redirect:/post/feed/list" : "redirect:/post/review/list";
    }
}
