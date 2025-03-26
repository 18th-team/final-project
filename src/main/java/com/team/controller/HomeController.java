package com.team.controller;

import com.team.authentication.AuthenticationDTO;
import com.team.authentication.AuthenticationService;
import com.team.user.*;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@RequiredArgsConstructor
@Controller
public class HomeController {
    private final UserService userService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @GetMapping("/")
    public String home(Model model, @AuthenticationPrincipal CustomSecurityUserDetails userDetails) {
        if (userDetails != null) { // sec:authorize로 보호되므로 null 체크는 선택적
            model.addAttribute("currentUser", userDetails.getSiteUser().getUuid()); // 이메일 추가
        }
        return "index";
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
            @RequestParam("profileImage") MultipartFile profileImage, HttpSession session) {
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
                        profileImage);
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
                        profileImage);
                return "redirect:/login";
            } else {
                bindingResult.rejectValue("phone", "duplicate.phone", "이미 가입된 전화번호입니다.");
                return "signup";
            }
        }
        userService.createSiteUser(
                userCreateForm.getName(),
                userCreateForm.getEmail(),
                userCreateForm.getPassword1(),
                userCreateForm.getBirthDay1(),
                userCreateForm.getBirthDay2(),
                userCreateForm.getPhone(),
                profileImage);
        return "redirect:/login";
    }
}
