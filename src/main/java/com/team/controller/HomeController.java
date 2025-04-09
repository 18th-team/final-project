package com.team.controller;

import com.team.moim.ClubDTO;
import com.team.moim.entity.Club;
import com.team.moim.entity.Keyword;
import com.team.moim.repository.ClubRepository;
import com.team.moim.repository.KeywordRepository;
import com.team.user.*;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
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
    private final KeywordRepository keywordRepository;

    @GetMapping("/map")
    public String mapApi() {
        return "mapApi";
    }

    @GetMapping("/")
    public String home(Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        ClubDTO clubDTO = new ClubDTO();

        //만약 비회원이라면?
        if (authentication == null || !authentication.isAuthenticated() || authentication.getPrincipal().equals("anonymousUser")) {
            // ClubRepository에서 모든 클럽 데이터를 가져오고, 이를 ClubDTO로 변환하여 리스트에 담기
            List<ClubDTO> clubList = clubRepository.findAll().stream()
                    .map(club -> ClubDTO.toDTO(club))  // Club 엔티티를 ClubDTO로 변환
                    .collect(Collectors.toList());
            if (!clubList.isEmpty()) {
                // 랜덤으로 섞기
                Collections.shuffle(clubList);
                // 최대 8개로 제한
                if (clubList.size() > 8) {
                    clubList = clubList.subList(0, 8); // 8개만 남기기
                }
            }
            // 키워드 목록 조회
            List<Keyword> keywordList = keywordRepository.findAll();
            model.addAttribute("keywordList", keywordList);
            model.addAttribute("clubList", clubList);
            return "index";
        }

        // 로그인된 사용자 처리
        String userEmail = authentication.getName();
        SiteUser user = userRepository.findByUuid(userEmail)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));
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
        // 랜덤으로 돌리기
        if (!recommendedClubDTOs.isEmpty()) {
            Collections.shuffle(recommendedClubDTOs);
            //최대3개제한
            if (recommendedClubDTOs.size() > 3) {
                recommendedClubDTOs = recommendedClubDTOs.subList(0, 3);
            }
        }
        // 모델에 변환된 ClubDTO 리스트 추가
        model.addAttribute("recommendedClubs", recommendedClubDTOs);

        List<ClubDTO> clubList = clubRepository.findAll().stream()
                .map(club -> ClubDTO.toDTO(club))  // Club 엔티티를 ClubDTO로 변환
                .collect(Collectors.toList());
        if (!clubList.isEmpty()) {
            // 랜덤으로 섞기
            Collections.shuffle(clubList);
            // 최대 8개로 제한
            if (clubList.size() > 8) {
                clubList = clubList.subList(0, 8); // 8개만 남기기
            }
        }

        // 키워드 목록 조회
        List<Keyword> keywordList = keywordRepository.findAll();
        model.addAttribute("keywordList", keywordList);
        model.addAttribute("clubList", clubList);
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
        Optional<SiteUser> existingUserByEmail = userRepository.findByUuid(userCreateForm.getEmail());
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
                        profileImage,
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
                profileImage,
                userCreateForm.getIntroduction(),
                keywordNames);
        return "redirect:/login";
    }
    /**
     * 검색을 요청할 페이지로 이동합니다.
     *
     * @param model 검색 결과를 뷰에 전달하기 위한 데이터 모델
     * @return 검색 결과를 표시할 뷰의 경로
     */
    @GetMapping(value = "/search")
    public String search(Model model) {
        return "api/search";
    }

}
