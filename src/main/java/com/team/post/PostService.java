package com.team.post;

import com.team.DataNotFoundException;
import com.team.moim.entity.Club;
import com.team.user.SiteUser;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class PostService {

    private final PostRepository postRepository;

    // ========================
    // 게시글 단건 조회
    // ========================
    public Post getPost(Integer id) {
        return postRepository.findById(id)
                .orElseThrow(() -> new DataNotFoundException("Post not found"));
    }

    // ========================
    // 작성
    // ========================
    public void create(String title, String content, String tags, String imageURL, Club club, SiteUser author, BoardType boardType) {
        Post post = new Post();
        post.setTitle(title);
        post.setContent(content);
        post.setTags(tags);
        post.setImageURL(imageURL);
        post.setAuthor(author);
        post.setClub(club);
        post.setCreateDate(LocalDateTime.now());
        post.setVoter(new HashSet<>());
        post.setBoardType(boardType); // 이거 꼭 추가
        postRepository.save(post);
    }

    // ========================
    // 수정
    // ========================
    public void modify(Post post, String title, String content, String tags, String imageURL, Club club) {
        post.setTitle(title);
        post.setContent(content);
        post.setTags(tags);
        post.setImageURL(imageURL);
        post.setClub(club);

        postRepository.save(post);
    }

    // ========================
    // 삭제
    // ========================
    @Transactional
    public void delete(Post post) {
        postRepository.delete(post);
    }

    // ========================
    // 좋아요
    // ========================
    public void vote(Post post, SiteUser user) {
        post.getVoter().add(user);
        postRepository.save(post);
    }

    public void cancelVote(Post post, SiteUser user) {
        post.getVoter().remove(user);
        postRepository.save(post);
    }

    // ========================
    // 공통 더보기 / 페이징
    // ========================
    public List<Post> findLimited(int offset, int limit) {
        Pageable pageable = PageRequest.of(offset / limit, limit, Sort.by(Sort.Direction.DESC, "createDate"));
        return postRepository.findAll(pageable).getContent();
    }

    public long count() {
        return postRepository.count();
    }

    // ========================
    // 피드 전용 (club == null)
    // ========================
    public List<Post> findFeedOnly(int offset, int limit) {
        Pageable pageable = PageRequest.of(offset / limit, limit, Sort.by(Sort.Direction.DESC, "createDate"));
        return postRepository.findByBoardType(BoardType.FEED, pageable).getContent();
    }

    public long countFeedOnly() {
        return postRepository.countByBoardType(BoardType.FEED);
    }


    public List<Post> searchFeedOnly(String keyword, int offset, int limit) {
        Pageable pageable = PageRequest.of(offset / limit, limit, Sort.by(Sort.Direction.DESC, "createDate"));
        return postRepository.findAllByKeyword(keyword, pageable)
                .stream().filter(p -> p.getClub() == null)
                .collect(Collectors.toList());
    }

    public long countFeedOnlyByKeyword(String keyword) {
        return postRepository.findAllByKeyword(keyword, Pageable.unpaged())
                .stream().filter(p -> p.getClub() == null)
                .count();
    }

    // ========================
    // 후기 전용 (club != null)
    // ========================
    public List<Post> findReviewOnly(int offset, int limit) {
        return findLimited(offset, limit).stream()
                .filter(p -> p.getClub() != null)
                .collect(Collectors.toList());
    }

    public long countReviewOnly() {
        return postRepository.findAll(Pageable.unpaged()).stream()
                .filter(p -> p.getClub() != null)
                .count();
    }

    public List<Post> searchReviewOnly(String keyword, int offset, int limit) {
        Pageable pageable = PageRequest.of(offset / limit, limit, Sort.by(Sort.Direction.DESC, "createDate"));
        return postRepository.findAllByKeyword(keyword, pageable)
                .stream().filter(p -> p.getClub() != null)
                .collect(Collectors.toList());
    }

    public long countReviewOnlyByKeyword(String keyword) {
        return postRepository.findAllByKeyword(keyword, Pageable.unpaged())
                .stream().filter(p -> p.getClub() != null)
                .count();
    }
}
