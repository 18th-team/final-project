package com.team.controller;

import com.team.moim.ClubDTO;
import com.team.moim.entity.Club;
import com.team.moim.entity.Keyword;
import com.team.moim.repository.ClubRepository;
import com.team.user.*;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.catalina.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Controller
public class HomeController {
    private final UserService userService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ClubRepository clubRepository;

    @GetMapping("/")
    public String home(Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated() || authentication.getPrincipal().equals("anonymousUser")) {
            model.addAttribute("userList", new ClubDTO());
            model.addAttribute("recommendedClubs", new ArrayList<>()); // 비회원의 경우 빈 리스트 전달
            return "index";
        }

        // 로그인된 사용자 처리
        String userEmail = authentication.getName();
        SiteUser user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));
        ClubDTO clubDTO = new ClubDTO();
        clubDTO.setHostName(user.getName());
        model.addAttribute("userList", clubDTO);

        // 사용자 선택 키워드 가져오기
        List<String> userKeywords = user.getKeywords().stream()
                .map(Keyword::getName)
                .collect(Collectors.toList());

// 1. Club 엔티티에서 키워드와 매칭되는 클럽을 조회
        List<Club> recommendedClubs = clubRepository.findByKeywords(userKeywords);

// 2. Club 엔티티 리스트를 ClubDTO 리스트로 변환
        List<ClubDTO> recommendedClubDTOs = recommendedClubs.stream()
                .map(club -> ClubDTO.toDTO(club))  // Club 엔티티에서 ClubDTO로 변환
                .collect(Collectors.toList());

// 모델에 변환된 ClubDTO 리스트 추가
        model.addAttribute("recommendedClubs", recommendedClubDTOs);

        return "index";  // view 이름 반환

    }


    @GetMapping("/mobti")
    public String mobtiTest() {
        return "mobti_test";
    }

    @GetMapping("/login")
    public String login(Model model) {
        return "login";
    }

    @GetMapping("/logout")
    public String logout() {
        return "redirect:/";
    }

    @GetMapping("/community")
    public String community() {
        return "feed_list";
    }

    @GetMapping("/signup")
    public String signUp(Model model) {
        System.out.println("Accessed /signup");
        String clientKey = UUID.randomUUID().toString();
        UserCreateForm userCreateForm = new UserCreateForm();
        userCreateForm.setClientKey(clientKey);
        model.addAttribute("userCreateForm", userCreateForm);
        return "signup";
    }

    @PostMapping("/signup")
    public String signUp(
            @Valid UserCreateForm userCreateForm,
            BindingResult bindingResult,
            @RequestParam("profileImage") MultipartFile profileImage,
            @RequestParam(value = "theme[]", required = false) List<String> keywordNames,
            HttpSession session) {
        // 폼 검증 에러 체크
        System.out.println(userCreateForm);
        if (bindingResult.hasErrors()) {
            return "signup";
        }

        // OTP 인증 여부 확인
        if (!"true".equals(userCreateForm.getOtpVerified())) {
            bindingResult.reject("otpNotVerified", "OTP 인증이 완료되지 않았습니다.");
            return "signup";
        } else {
            Object otpVerifiedObj = session.getAttribute("otpVerified_" + userCreateForm.getClientKey());
            boolean otpVerified = otpVerifiedObj instanceof Boolean && (Boolean) otpVerifiedObj;
            if (!otpVerified) {
                bindingResult.reject("otpNotVerified", "OTP 인증이 완료되지 않았습니다.");
                return "signup";
            }
        }
        // 이메일 중복 확인
        Optional<SiteUser> existingUserByEmail = userRepository.findByEmail(userCreateForm.getEmail());
        if (existingUserByEmail.isPresent()) {
            SiteUser user = existingUserByEmail.get();
            if (user.getProvider() != null && user.getProviderId() != null) {
                // OAuth 계정인 경우 비밀번호와 프로필 이미지 업데이트
                userService.createSiteUser(
                        userCreateForm.getName(),
                        userCreateForm.getEmail(),
                        userCreateForm.getPassword1(),
                        userCreateForm.getBirthDay1(),
                        userCreateForm.getBirthDay2(),
                        userCreateForm.getPhone(),
                        profileImage,
                        userCreateForm.getIntroduction(),
                        keywordNames
                );
                return "redirect:/login";
            } else {
                bindingResult.rejectValue("email", "duplicate.email", "이미 가입된 이메일입니다.");
                return "signup";
            }
        }
        // 전화번호 중복 확인
        Optional<SiteUser> existingUserByPhone = userRepository.findByPhone(userCreateForm.getPhone());
        if (existingUserByPhone.isPresent()) {
            SiteUser user = existingUserByPhone.get();
            if (user.getProvider() != null && user.getProviderId() != null) {
                // OAuth 계정인 경우 비밀번호와 프로필 이미지 업데이트
                userService.createSiteUser(
                        userCreateForm.getName(),
                        userCreateForm.getEmail(),
                        userCreateForm.getPassword1(),
                        userCreateForm.getBirthDay1(),
                        userCreateForm.getBirthDay2(),
                        userCreateForm.getPhone(),
                        profileImage
                        ,
                        userCreateForm.getIntroduction(), keywordNames
                );
                return "redirect:/login";
            } else {
                bindingResult.rejectValue("phone", "duplicate.phone", "이미 가입된 전화번호입니다.");
                return "signup";
            }
        }
        //신규 사용자 생성
        userService.createSiteUser(
                userCreateForm.getName(),
                userCreateForm.getEmail(),
                userCreateForm.getPassword1(),
                userCreateForm.getBirthDay1(),
                userCreateForm.getBirthDay2(),
                userCreateForm.getPhone(),
                profileImage
                ,
                userCreateForm.getIntroduction(),
                keywordNames);
        return "redirect:/login";
    }

}
