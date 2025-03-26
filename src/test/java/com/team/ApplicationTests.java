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
import java.util.UUID;

@SpringBootTest
class ApplicationTests {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

	@Test
	void contextLoads() {
		SiteUser user1 = SiteUser.builder()
				.name("테스트용1")
				.email("test1@t")
				.password(passwordEncoder.encode("1"))
				.age(1)
				.gender("남성")
				.phone("010-1234-5671")
				.profileImage("/images/test1.jpg")
				.money(10000)
				.createdAt(LocalDate.now())
				.role(MemberRole.USER)
				.provider(null) // 폼 로그인
				.providerId(null)
				.uuid(UUID.randomUUID().toString())
				.build();
		userRepository.save(user1);
		SiteUser user2 = SiteUser.builder()
				.name("테스트용2")
				.email("test2@t")
				.password(passwordEncoder.encode("1"))
				.age(1)
				.gender("남성")
				.phone("010-1234-5674")
				.profileImage("/images/test1.jpg")
				.money(10000)
				.createdAt(LocalDate.now())
				.role(MemberRole.USER)
				.provider(null) // 폼 로그인
				.providerId(null)
				.uuid(UUID.randomUUID().toString())
				.build();
		userRepository.save(user2);
	}

}
