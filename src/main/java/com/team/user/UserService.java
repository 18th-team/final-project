package com.team.user;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
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

    public SiteUser createSiteUser(String name, String email, String password, String birthDay1, String birthDay2, String phone, MultipartFile profileImage){
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
                existingUser.setPassword(passwordEncoder.encode(password));
                return userRepository.save(existingUser);
            }
            // provider와 providerId가 없는 경우 (일반 계정) -> 컨트롤러에서 처리
        }
        // 빌더 시작
        SiteUser.SiteUserBuilder builder = SiteUser.builder()
                .name(name)
                .email(email)
                .password(passwordEncoder.encode(password)) // 비밀번호 암호화
                .phone(phone)
                .uuid(String.valueOf(UUID.randomUUID()))
                .role(MemberRole.USER);

        // 성별 계산
        if (birthDay2.equals("1") || birthDay2.equals("3")) {
            builder.gender("남자");
        } else if (birthDay2.equals("2") || birthDay2.equals("4")) {
            builder.gender("여자");
        }

        // 나이 계산 로직
        String yearPrefix = (Integer.parseInt(birthDay1.substring(0, 2)) >= 0 &&
                Integer.parseInt(birthDay1.substring(0, 2)) <= 24) ? "20" : "19";
        String fullBirthDate = yearPrefix + birthDay1;
        LocalDate birthDate = LocalDate.parse(fullBirthDate, java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        LocalDate currentDate = LocalDate.now();
        int age = currentDate.getYear() - birthDate.getYear();
        builder.age(age);

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
        builder.createdAt(currentDate);
        builder.money(0);
        // SiteUser 객체 생성 및 저장
        SiteUser siteUser = builder.build();
        return userRepository.save(siteUser);
    }
}