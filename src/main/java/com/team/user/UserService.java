package com.team.user;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@RequiredArgsConstructor
@Service
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public SiteUser createSiteUser(String name, String email, String password, String birthDay1, String birthDay2, String phone, String profileImage) {
        SiteUser siteUser = new SiteUser();
        siteUser.setName(name);
        siteUser.setPassword(password);
        siteUser.setEmail(email);

        if(birthDay2.equals("1") || birthDay2.equals("3")){
            siteUser.setGender("남자");
        }else if(birthDay2.equals("2") || birthDay2.equals("4")){
            siteUser.setGender("여자");
        }
        //나이 계산 로직
        // "YYMMDD" 형식에서 연도 변환 (1900년대 또는 2000년대 구분)
        String yearPrefix = (Integer.parseInt(birthDay1.substring(0, 2)) >= 0 &&
                Integer.parseInt(birthDay1.substring(0, 2)) <= 24) ? "20" : "19";
        String fullBirthDate = yearPrefix + birthDay1;
        // LocalDate로 변환
        LocalDate birthDate = LocalDate.parse(fullBirthDate, java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        LocalDate currentDate = LocalDate.now();
        // 연 나이 계산 (생일 여부 상관없이 연도 차이만 계산)
        int age = currentDate.getYear() - birthDate.getYear();
        siteUser.setAge(age);
        siteUser.setPassword(passwordEncoder.encode(password));
        siteUser.setPhone(phone);
        siteUser.setProfileImage(profileImage);
        this.userRepository.save(siteUser);
        return siteUser;
    }
}
