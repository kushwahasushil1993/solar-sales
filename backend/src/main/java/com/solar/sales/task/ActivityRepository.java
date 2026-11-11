package com.solar.sales.task;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ActivityRepository extends JpaRepository<Activity, Long> {

    List<Activity> findByAgentIdOrderByCreatedAtDescIdDesc(Long agentId, Pageable pageable);

    List<Activity> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    /** [agentId, latest activity time] */
    @Query("select a.agentId, max(a.createdAt) from Activity a where a.agentId is not null group by a.agentId")
    List<Object[]> lastActivityPerAgent();
}
