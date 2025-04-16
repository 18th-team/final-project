package com.team.chat;

import com.team.user.SiteUser;
import com.team.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserSearchController {
    private final UserRepository userRepository;
    // 사용자 검색 엔드포인트
    @GetMapping("/search")
    public ResponseEntity<List<ChatRoomDTO.SiteUserDTO>> searchUserByUuid(@RequestParam("uuid") String uuid) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getName())) {
            // 로그인하지 않은 사용자는 검색 불가 (혹은 다른 정책)
            return ResponseEntity.status(401).body(Collections.emptyList());
        }
        String currentUserUuid = authentication.getName(); // Principal의 getName()이 UUID를 반환한다고 가정

        // 입력된 UUID가 현재 사용자 UUID와 같으면 검색 결과 없음 처리
        if (currentUserUuid.equals(uuid)) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        Optional<SiteUser> userOpt = userRepository.findByUuid(uuid); // UUID로 사용자 검색

        if (userOpt.isPresent()) {
            SiteUser foundUser = userOpt.get();
            // 검색된 사용자를 DTO로 변환하여 리스트에 담아 반환
            ChatRoomDTO.SiteUserDTO userDto = new ChatRoomDTO.SiteUserDTO(
                    foundUser.getUuid(),
                    foundUser.getName(), // 또는 getUsername()
                    foundUser.getProfileImage()
            );
            return ResponseEntity.ok(List.of(userDto)); // 단일 사용자 DTO 리스트 반환
        } else {
            // 사용자를 찾지 못한 경우 빈 리스트 반환
            return ResponseEntity.ok(Collections.emptyList());
        }
    }
}
