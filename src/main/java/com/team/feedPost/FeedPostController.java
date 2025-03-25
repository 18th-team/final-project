package com.team.feedPost;

import com.team.FileService;
import com.team.feedComment.FeedCommentService;
import com.team.user.SiteUser;
import com.team.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;

@RequestMapping("/feed")
@RequiredArgsConstructor
@Controller
public class FeedPostController {

    private final FeedPostService feedPostService;
    private final UserService userService;
    private final FileService fileService;
    private final FeedCommentService feedCommentService;


    @GetMapping("/list")
    public String feedList(@RequestParam(value="keyword", required = false) String keyword, Model model, Principal principal) {
        int offset = 0;
        int limit = 4;

        List<FeedPost> feedList = (keyword != null && !keyword.isEmpty()) ?
                feedPostService.searchByKeyword(keyword, offset, limit) :
                feedPostService.findLimited(offset, limit);

        long totalCount = (keyword != null && !keyword.isEmpty()) ?
                feedPostService.countByKeyword(keyword) :
                feedPostService.count();

        model.addAttribute("feedList", feedList);
        model.addAttribute("hasMore", totalCount > limit); // 더보기 버튼 표시 여부
        model.addAttribute("keyword", keyword); // 검색어 유지용

        // 로그인 사용자 정보 전달
        if (principal != null) {
            SiteUser loginUser = userService.getUser(principal.getName());
            model.addAttribute("loginUser", loginUser);
        }

        // 댓글 목록, 댓글 수 전달
        model.addAttribute("allComments", feedCommentService.getAllCommentsMap(feedList));
        model.addAttribute("commentCountMap", feedCommentService.getCommentCountMap(feedList));


        return "feed_list";
    }

    @GetMapping("/more")
    @ResponseBody
    public List<FeedPost> loadMorePosts(@RequestParam int offset, @RequestParam int limit) {
        return feedPostService.findLimited(offset, limit);
    }


    @GetMapping("/create")
    public String feedForm(Model model) {
        model.addAttribute("feedForm", new FeedPostForm());
        return "feed_form";
    }

    @PostMapping("/create")
    public String createFeedPost(@ModelAttribute FeedPostForm feedForm,
                                 @RequestParam("imageURL") MultipartFile imageFile,
                                 Principal principal) {
        SiteUser user = userService.getUser(principal.getName());

        String imagePath = null;
        if (!imageFile.isEmpty()) {
            imagePath = fileService.saveImage(imageFile); // 이미지 저장 후 경로 반환
        }

        feedPostService.create(
                feedForm.getTitle(),
                feedForm.getContent(),
                feedForm.getTags(),
                imagePath,
                user
        );

        return "redirect:/feed/list"; // 작성 후 이동할 경로
    }

    @GetMapping("/vote/{id}")
    public String vote(@PathVariable("id") Integer id, Principal principal) {
        FeedPost feedPost = feedPostService.getFeedPost(id);
        SiteUser siteUser = userService.getUser(principal.getName());

        if (feedPost.getVoter().contains(siteUser)) {
            feedPostService.cancelVote(feedPost, siteUser); // 좋아요 취소
        } else {
            feedPostService.vote(feedPost, siteUser);       // 좋아요 추가
        }
        return "redirect:/feed/list";
    }

    @GetMapping("/modify/{id}")
    public String modifyForm(@PathVariable Integer id, Principal principal, Model model) {
        FeedPost post = feedPostService.getFeedPost(id);
        SiteUser user = userService.getUser(principal.getName());

        if (!post.getAuthor().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        FeedPostForm form = new FeedPostForm();
        form.setTitle(post.getTitle());
        form.setContent(post.getContent());
        model.addAttribute("feedForm", form);
        model.addAttribute("postID", post.getPostID());

        return "feed_form"; // 글쓰기와 동일한 템플릿 사용
    }

    @PostMapping("/modify/{id}")
    public String modifySubmit(@PathVariable Integer id,
                               @ModelAttribute FeedPostForm form,
                               Principal principal) {
        FeedPost post = feedPostService.getFeedPost(id);
        SiteUser user = userService.getUser(principal.getName());

        if (!post.getAuthor().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        feedPostService.modify(post, form.getTitle(), form.getContent());
        return "redirect:/feed/list";
    }

    @GetMapping("/delete/{id}")
    public String delete(@PathVariable Integer id, Principal principal) {
        FeedPost post = feedPostService.getFeedPost(id);
        SiteUser user = userService.getUser(principal.getName());

        if (!post.getAuthor().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        feedPostService.delete(post);
        return "redirect:/feed/list";
    }


}
