package com.solar.sales.repository;

import com.solar.sales.entity.AppUser;
import com.solar.sales.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmail(String email);

    boolean existsByEmail(String email);

    long countByRoleAndEnabledTrue(Role role);

    List<AppUser> findAllByOrderByCreatedAtDesc();
}
