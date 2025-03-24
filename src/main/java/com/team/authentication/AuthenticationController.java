package com.team.authentication;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@RequiredArgsConstructor
@Controller
public class AuthenticationController {
    private static final Pattern PHONE_PATTERN = Pattern.compile("^010\\d{8}$");

    @PostMapping("/get-captcha")
    public Mono<ResponseEntity<String>> getCaptcha(
            @RequestParam("clientKey") String clientKey,
            @RequestParam(value = "cnt", required = false) Integer cnt,
            HttpSession session) {
        AuthenticationService authService = new AuthenticationService(session, clientKey);
        if (clientKey == null || clientKey.isEmpty()) {
            return Mono.just(ResponseEntity.badRequest().body("clientKey 파라미터가 필요합니다."));
        }
        return authService.getCaptchaImage(cnt)
                .map(captchaImageUrl -> ResponseEntity.ok(captchaImageUrl))
                .defaultIfEmpty(ResponseEntity.badRequest().body("캡차 이미지 생성 실패"));
    }
    @PostMapping("/send-otp")
    public Mono<ResponseEntity<String>> sendOtp(
            @RequestParam("clientKey") String clientKey,
            @RequestParam("name") String name,
            @RequestParam("birthDay1") String birthDay1,
            @RequestParam("birthDay2") String birthDay2,
            @RequestParam("cellCorp") String cellCorp,
            @RequestParam("phone") String phone,
            @RequestParam("captchaInput") String captchaInput,
            HttpSession session) {
        if (clientKey == null || clientKey.isEmpty()) {
            return Mono.just(ResponseEntity.badRequest().body("clientKey 파라미터가 필요합니다."));
        }
        if (name == null || name.isEmpty()) {
            return Mono.just(ResponseEntity.badRequest().body("이름을 입력 해주세요."));
        }
        if (birthDay1 == null || birthDay1.isEmpty()) {
            return Mono.just(ResponseEntity.badRequest().body("생일을 입력 해주세요."));
        }
        if (birthDay2 == null || birthDay2.isEmpty()) {
            return Mono.just(ResponseEntity.badRequest().body("생일을 입력 해주세요."));
        }
        if (cellCorp == null || cellCorp.isEmpty()) {
            return Mono.just(ResponseEntity.badRequest().body("통신사를 입력 해주세요."));
        }
        if (captchaInput == null || captchaInput.isEmpty()) {
            return Mono.just(ResponseEntity.badRequest().body("보안문자를 입력 해주세요."));
        }
        if (phone == null || phone.isEmpty() || !PHONE_PATTERN.matcher(phone).matches()) {
            return Mono.just(ResponseEntity.badRequest().body("전화번호는 010으로 시작하고 11자리 숫자여야 합니다."));
        }

        AuthenticationService authService = new AuthenticationService(session, clientKey);
        AuthenticationDTO dto = new AuthenticationDTO();
        dto.setName(name);
        dto.setBirthDay1(birthDay1);
        dto.setBirthDay2(birthDay2);
        dto.setPhone(phone);
        dto.setCellcorp(cellCorp);

        return authService.updatedUrl()
                .then(Mono.fromCallable(() -> {
                    String updatedUrl = (String) session.getAttribute("updatedUrl_" + clientKey);
                    if (updatedUrl == null || updatedUrl.isEmpty()) {
                        throw new RuntimeException("updatedUrl이 설정되지 않았습니다.");
                    }
                    return updatedUrl;
                }))
                .flatMap(url -> authService.extractReqInfoAndRetUrl(clientKey))
                .flatMap(formData -> {
                    if (formData.containsKey("alertText")) {
                        return Mono.just(ResponseEntity.badRequest().body("정보 추출 실패: " + formData.getFirst("alertText")));
                    }
                    return authService.CertificationRequest(dto, formData, captchaInput)
                            .map(result -> ResponseEntity.ok("OTP가 성공적으로 전송되었습니다."))
                            .onErrorResume(e -> Mono.just(ResponseEntity.badRequest().body("OTP 전송 실패: " + e.getMessage())));
                })
                .onErrorResume(e -> Mono.just(ResponseEntity.badRequest().body("초기화 실패: " + e.getMessage())));
    }
    @PostMapping("/check-otp")
    public Mono<ResponseEntity<String>> checkOtp(
            @RequestParam("clientKey") String clientKey,
            @RequestParam("otpInput") String otp,
            HttpSession session) {
        if (clientKey == null || clientKey.isEmpty()) {
            return Mono.just(ResponseEntity.badRequest().body("clientKey 파라미터가 필요합니다."));
        }
        if (otp == null || otp.isEmpty()) {
            return Mono.just(ResponseEntity.badRequest().body("인증번호를 입력 해주세요."));
        }

        AuthenticationService authService = new AuthenticationService(session, clientKey);
        MultiValueMap<String, String> formData = (MultiValueMap<String, String>) session.getAttribute("resultFormData_" + clientKey);
        if (formData == null) {
            return Mono.just(ResponseEntity.badRequest().body("인증 요청 데이터가 없습니다. OTP를 먼저 요청해주세요."));
        }

        return authService.checkOtp(formData, otp)
                .flatMap(result -> {
                    if (result.containsKey("alertText") && "인증이 정상적으로 처리되었습니다.".equals(result.getFirst("alertText"))) {
                        session.setAttribute("otpVerified_" + clientKey, true);
                        return Mono.just(ResponseEntity.ok("인증이 완료되었습니다."));
                    }
                    return Mono.just(ResponseEntity.badRequest().body(result.getFirst("alertText")));
                })
                .onErrorResume(e -> Mono.just(ResponseEntity.badRequest().body("OTP 검증 실패: " + e.getMessage())));
    }
}
