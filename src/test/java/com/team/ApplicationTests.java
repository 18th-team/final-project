package com.team;

import com.team.user.SiteUser;
import com.team.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;

@SpringBootTest
class ApplicationTests {

    @Autowired
    private UserRepository userRepository;

	@Test
	void contextLoads() {
		Optional<SiteUser> user = userRepository.findByProviderAndProviderId("naver", "COQIUIwYH4RIU8yk-06XRFia36O1c6o65vz2cVxy6YM");
		System.out.println("Found user: " + user.map(SiteUser::getEmail).orElse("Not found"));
	}

}
