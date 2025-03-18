package com.team.authentication;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Controller
public class AuthenticationController {
    @GetMapping("/test")
    public Mono<String> index(Model model, HttpSession session) {
        String clientKey = UUID.randomUUID().toString();
        AuthenticationService authService = new AuthenticationService(session, clientKey);
        return authService.cookieSetup()
                .then(authService.getCaptchaImage())
                .map(captchaImage -> {
                    model.addAttribute("captchaImage", captchaImage);
                    model.addAttribute("clientKey", authService.getClientKey()); // clientKey 전달
                    return "test";
                })
                .onErrorResume(e -> {
                    System.out.println("쿠키 설정 실패: " + e.getMessage());
                    model.addAttribute("error", "캡차 이미지를 불러오는 중 오류가 발생했습니다.");
                    return Mono.just("test");
                });
    }
    @GetMapping("/otp")
    public Mono<String> otpCheck(Model model, HttpSession session,  @RequestParam("clientKey") String clientKey) {
        AuthenticationService authService = new AuthenticationService(session, clientKey);
        model.addAttribute("clientKey", authService.getClientKey());
        return Mono.just("test2");
    }
    @PostMapping ("/otp")
    public Mono<String> otpCheckPost(Model model, HttpSession session,  @RequestParam("clientKey") String clientKey, @RequestParam("otp") String otp) {
        AuthenticationService authService = new AuthenticationService(session, clientKey);
        MultiValueMap<String, String> resultFormData = (MultiValueMap<String, String>) session.getAttribute("resultFormData_" + clientKey);
        if (resultFormData == null) {
            model.addAttribute("error", "인증 데이터가 없습니다. 다시 시도해주세요.");
            return Mono.just("redirect:/test");
        }
        return authService.checkOtp(resultFormData, otp).flatMap(data -> {
            model.addAttribute("error", "결과 : " + data);
            return Mono.just("test2");
        });
    }
    @PostMapping("/test")
    public Mono<String> submit(
            @RequestParam("cellCorp") String cellCorp,
            @RequestParam("userName") String userName,
            @RequestParam("birthDay1") String birthDay1,
            @RequestParam("birthDay2") String birthDay2,
            @RequestParam("No") String No,
            @RequestParam("captchaInput") String captchaInput,
            @RequestParam("clientKey") String clientKey, // clientKey 받기
            Model model,
            HttpSession session) {

        AuthenticationDTO authenticationDTO = new AuthenticationDTO();
        authenticationDTO.setCellcorp(cellCorp);
        authenticationDTO.setPhone(No);
        authenticationDTO.setName(userName);
        authenticationDTO.setBirthDay1(birthDay1);
        authenticationDTO.setBirthDay2(birthDay2);
        authenticationDTO.setCaptchaInput(captchaInput);

        AuthenticationService authService = new AuthenticationService(session, clientKey);
        return authService.extractReqInfoAndRetUrl(clientKey)
                .flatMap(formData -> {
                    System.out.println("Extracted formData for clientKey: " + clientKey + " - " + formData);
                    List<String> alertText = formData.get("alertText");
                    if (alertText != null && !alertText.isEmpty()) {
                        model.addAttribute("error", alertText.get(0));
                        return authService.cookieSetup()
                                .then(authService.getCaptchaImage())
                                .map(captchaImage -> {
                                    model.addAttribute("captchaImage", captchaImage);
                                    model.addAttribute("clientKey", authService.getClientKey());
                                    return "test";
                                });
                    } else {
                        return authService.CertificationRequest(authenticationDTO, formData)
                                .flatMap(resultFormData -> {
                                    System.out.println("CertificationRequest result formData: " + resultFormData);
                                    List<String> alertText2 = resultFormData.get("alertText");
                                    if (alertText2 != null && !alertText2.isEmpty()) {
                                        model.addAttribute("error", alertText2.get(0));
                                        return authService.cookieSetup()
                                                .then(authService.getCaptchaImage())
                                                .map(captchaImage -> {
                                                    model.addAttribute("captchaImage", captchaImage);
                                                    model.addAttribute("clientKey", authService.getClientKey());
                                                    return "test";
                                                });
                                    }
                                    return Mono.just("redirect:/otp?clientKey=" + authService.getClientKey());
                                });
                    }
                })
                .onErrorResume(e -> {
                    System.out.println("인증 요청 처리 중 오류 발생: " + e.getMessage());
                    e.printStackTrace();
                    model.addAttribute("error", "인증 처리 중 오류가 발생했습니다: " + e.getMessage());
                    return authService.cookieSetup()
                            .then(authService.getCaptchaImage())
                            .map(captchaImage -> {
                                model.addAttribute("captchaImage", captchaImage);
                                model.addAttribute("clientKey", authService.getClientKey());
                                return "test";
                            });
                });
    }
}