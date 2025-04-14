package com.team;

import com.team.chat.ChatRoomService;
import com.team.moim.entity.Club;
import com.team.moim.entity.Keyword;
import com.team.moim.repository.ClubRepository;
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
class ApplicationTests {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private KeywordRepository keywordRepository;

	@Test
	void contextLoads() {
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
				.birthdate(LocalDate.now())
				.gender("남성")
				.phone("010-1234-5671")
				.profileImage("/img/test_main.png")
				.money(10000)
				.createdAt(LocalDate.now())
				.role(MemberRole.USER)
				.provider(null) // 폼 로그인
				.providerId(null)
				.uuid(UUID.randomUUID().toString())
				.introduction("테스트용 계정 입니다")
				.keywords(keywords)
				.lastOnline(null)
				.build();
		userRepository.save(user1);
		SiteUser user2 = SiteUser.builder()
				.name("테스트용2")
				.email("test2@t")
				.password(passwordEncoder.encode("1"))
				.birthdate(LocalDate.now())
				.gender("남성")
				.phone("010-1234-5674")
				.profileImage("/img/test1.jpg")
				.money(10000)
				.createdAt(LocalDate.now())
				.role(MemberRole.USER)
				.provider(null) // 폼 로그인
				.providerId(null)
				.uuid(UUID.randomUUID().toString())
				.introduction("테스트용 계정 입니다")
				.keywords(keywords)
				.lastOnline(null)
				.build();
		userRepository.save(user2);

		SiteUser user3 = SiteUser.builder()
				.name("테스트용3")
				.email("test3@t")
				.password(passwordEncoder.encode("1"))
				.birthdate(LocalDate.now())
				.gender("남성")
				.phone("010-1234-2412")
				.profileImage("/img/test1.jpg")
				.money(10000)
				.createdAt(LocalDate.now())
				.role(MemberRole.USER)
				.provider(null) // 폼 로그인
				.providerId(null)
				.uuid(UUID.randomUUID().toString())
				.introduction("테스트용 계정 입니다")
				.keywords(keywords)
				.lastOnline(null)
				.build();
		userRepository.save(user3);
/*        chatRoomService.CreateMoimChatRoom("모임이름", "7cd06dce-2e70-497a-8fec-cb7482c06258");*/
	}

}
