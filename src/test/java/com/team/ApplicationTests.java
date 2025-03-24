package com.team;

import com.team.user.MemberRole;
import com.team.user.SiteUser;
import com.team.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.Optional;

@SpringBootTest
class ApplicationTests {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

	@Test
	void contextLoads() {
		SiteUser user1 = SiteUser.builder()
				.name("김테스트")
				.email("test1@test.com")
				.password(passwordEncoder.encode(""))
				.age(25)
				.gender("남성")
				.phone("010-1234-5678")
				.profileImage("/images/test1.jpg")
				.money(10000)
				.createdAt(LocalDate.now())
				.role(MemberRole.USER)
				.provider(null) // 폼 로그인
				.providerId(null)
				.build();
		userRepository.save(user1);
	}

}
