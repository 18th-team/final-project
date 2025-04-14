package com.team.user;

import com.team.moim.entity.Keyword;
import com.team.moim.repository.KeywordRepository;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final KeywordRepository keywordRepository;
    private static final String UPLOAD_DIR = "src/main/resources/static/img/user/";

    // 이메일 중복 확인 메서드
    public boolean isEmailAlreadyExists(String email) {
        Optional<SiteUser> existingUserByEmail = userRepository.findByEmail(email);
        if (existingUserByEmail.isPresent()) {
            SiteUser existingUser = existingUserByEmail.get();
            // provider와 providerId가 없는 경우 (일반 계정)만 true 반환
            return existingUser.getProvider() == null && existingUser.getProviderId() == null&& existingUser.getPassword() == null;
        }
        return false;
    }

    // 전화번호 중복 확인 메서드
    public boolean isPhoneAlreadyExists(String phone) {
        return userRepository.findByPhone(phone).isPresent();
    }

    //회원정보 생성 로직
    public SiteUser createSiteUser(String name, String email, String password, String birthDay1, String birthDay2, String phone, MultipartFile profileImage, String introduction, List<String> keywordNames) {
        // 이메일 중복 확인
        Optional<SiteUser> existingUserByEmail = userRepository.findByEmail(email);
        if (existingUserByEmail.isPresent()) {
            SiteUser existingUser = existingUserByEmail.get();
            // provider와 providerId가 있는 경우 (소셜 계정) -> password만 업데이트
            if (existingUser.getProvider() != null && existingUser.getProviderId() != null) {
                // 프로필 이미지 처리
                if (!profileImage.isEmpty()) {
                    try {
                        String originalFileName = profileImage.getOriginalFilename();
                        String extension = originalFileName != null ? originalFileName.substring(originalFileName.lastIndexOf(".")) : "";
                        String fileName = UUID.randomUUID() + extension;
                        Path filePath = Paths.get(UPLOAD_DIR, fileName);

                        Files.createDirectories(filePath.getParent());
                        Files.write(filePath, profileImage.getBytes());
                        existingUser.setProfileImage("/img/user/" + fileName);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
                // 키워드 업데이트 (name 중복 해결)
                Set<Keyword> keywords = keywordNames != null
                        ? keywordNames.stream()
                        .map(keywordName -> keywordRepository.findByName(keywordName)
                                .orElseGet(() -> keywordRepository.save(new Keyword(null, keywordName))))
                        .collect(Collectors.toSet())
                        : new HashSet<>();
                existingUser.setKeywords(keywords);
                return userRepository.save(existingUser);
            }
            // provider와 providerId가 없는 경우 (일반 계정) -> 컨트롤러에서 처리
        }

        // 키워드 변환 (여기도 동일하게 수정)
        Set<Keyword> keywords = keywordNames != null
                ? keywordNames.stream()
                .map(keywordName -> keywordRepository.findByName(keywordName)
                        .orElseGet(() -> keywordRepository.save(new Keyword(null, keywordName))))
                .collect(Collectors.toSet())
                : new HashSet<>();

        // 신규 사용자 생성
        SiteUser.SiteUserBuilder builder = SiteUser.builder()
                .name(name)
                .email(email)
                .password(passwordEncoder.encode(password)) // 비밀번호 암호화
                .phone(phone)
                .introduction(introduction)
                .keywords(keywords)
                .uuid(String.valueOf(UUID.randomUUID()))
                .role(MemberRole.USER);

        // 성별 계산
        if (birthDay2.equals("1") || birthDay2.equals("3")) {
            builder.gender("남자");
        } else if (birthDay2.equals("2") || birthDay2.equals("4")) {
            builder.gender("여자");
        }

        // birthdate 계산
        String yearPrefix = (Integer.parseInt(birthDay1.substring(0, 2)) >= 0 &&
                Integer.parseInt(birthDay1.substring(0, 2)) <= 24) ? "20" : "19";
        String fullBirthDate = yearPrefix + birthDay1;
        LocalDate birthdate = LocalDate.parse(fullBirthDate, DateTimeFormatter.BASIC_ISO_DATE);
        builder.birthdate(birthdate);
        // 프로필 이미지 처리
        if (!profileImage.isEmpty()) {
            try {
                String originalFileName = profileImage.getOriginalFilename();
                String extension = originalFileName != null ? originalFileName.substring(originalFileName.lastIndexOf(".")) : "";
                String fileName = UUID.randomUUID() + extension;
                Path filePath = Paths.get(UPLOAD_DIR, fileName);

                Files.createDirectories(filePath.getParent());
                Files.write(filePath, profileImage.getBytes());
                builder.profileImage("/img/user/" + fileName);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        builder.createdAt(LocalDate.now());
        builder.money(0);
        // SiteUser 객체 생성 및 저장
        SiteUser siteUser = builder.build();
        return userRepository.save(siteUser);
    }

    public SiteUser getUser(String email) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        System.out.println("현재 사용자: " + auth);  // 여기서 auth 정보 확인
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));
    }

    public SiteUser getUserByUuid(String uuid) {
        return userRepository.findByUuid(uuid)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));
    }

    public void save(SiteUser user) {
        userRepository.save(user);
    }


}