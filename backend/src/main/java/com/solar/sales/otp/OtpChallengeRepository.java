package com.solar.sales.otp;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OtpChallengeRepository extends JpaRepository<OtpChallenge, Long> {

    Optional<OtpChallenge> findFirstByPhoneOrderByCreatedAtDescIdDesc(String phone);

    List<OtpChallenge> findByPhoneAndConsumedFalse(String phone);

    long countByPhoneAndCreatedAtAfter(String phone, LocalDateTime since);

    long countByCreatedAtAfter(LocalDateTime since);
}
