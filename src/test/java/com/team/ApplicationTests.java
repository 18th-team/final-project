package com.team;

import com.team.moim.entity.Keyword;
import com.team.moim.repository.KeywordRepository;
import com.team.user.MemberRole;
import com.team.user.SiteUser;
import com.team.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;


@SpringBootTest
public class ApplicationTests {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private KeywordRepository keywordRepository;

	@Test
	public void contextLoads() {
		List<String> keywordNames = new ArrayList<>();
		keywordNames.add("액티비티");
		keywordNames.add("자기계발");
		Set<Keyword> keywords = keywordNames != null
				? keywordNames.stream()
				.map(keywordName -> keywordRepository.findByName(keywordName)
						.orElseGet(() -> keywordRepository.save(new Keyword(null, keywordName))))
				.collect(Collectors.toSet())
				: new HashSet<>();
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
				.introduction("테스트용 계정 입니다")
				.keywords(keywords)
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
				.introduction("테스트용 계정 입니다")
				.keywords(keywords)
				.build();
		userRepository.save(user2);
	}

}
