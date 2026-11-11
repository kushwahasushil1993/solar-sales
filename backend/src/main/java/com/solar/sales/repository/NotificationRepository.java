package com.solar.sales.repository;

import com.solar.sales.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findBySaleIdOrderByCreatedAtDescIdDesc(Long saleId);

    Page<Notification> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);
}
