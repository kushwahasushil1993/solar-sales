package com.solar.sales.task;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface InboxRepository extends JpaRepository<InboxNotification, Long> {

    List<InboxNotification> findByRecipientIdOrderByCreatedAtDescIdDesc(Long recipientId, Pageable pageable);

    long countByRecipientIdAndReadFalse(Long recipientId);

    Optional<InboxNotification> findByIdAndRecipientId(Long id, Long recipientId);

    @Modifying
    @Query("update InboxNotification n set n.read = true where n.recipientId = :recipientId and n.read = false")
    int markAllRead(Long recipientId);
}
