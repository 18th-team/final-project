package com.team.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<SiteUser, Long> {

    // 이메일로 사용자 조회
    Optional<SiteUser> findByEmail(String email);

    // 전화번호로 사용자 조회
    Optional<SiteUser> findByPhone(String phone);

    // OAuth 제공자와 제공자 ID로 사용자 조회
    Optional<SiteUser> findByProviderAndProviderId(String provider, String providerId);

    // 이름과 이메일로 사용자 존재 여부 확인
    boolean existsByNameAndEmail(String name, String email);
    Optional<SiteUser> findByUuid(String uuid);

    @Query("SELECT u FROM SiteUser u LEFT JOIN FETCH u.blockedUsers WHERE u.uuid = :uuid")
    Optional<SiteUser> findByUuidWithBlockedUsers(@Param("uuid") String uuid);
}