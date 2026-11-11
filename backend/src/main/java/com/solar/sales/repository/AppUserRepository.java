package com.solar.sales.repository;

import com.solar.sales.entity.AppUser;
import com.solar.sales.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<AppUser> findByPhone(String phone);

    boolean existsByPhone(String phone);

    long countByRoleAndEnabledTrue(Role role);

    long countByRole(Role role);

    List<AppUser> findByRoleAndEnabledTrue(Role role);

    List<AppUser> findByRoleOrderByFullNameAsc(Role role);

    List<AppUser> findAllByOrderByCreatedAtDesc();
}
