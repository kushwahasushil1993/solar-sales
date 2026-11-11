package com.solar.sales.repository;

import com.solar.sales.entity.InstallationStatus;
import com.solar.sales.entity.SolarSale;
import com.solar.sales.entity.TaskState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface SolarSaleRepository extends JpaRepository<SolarSale, Long>, JpaSpecificationExecutor<SolarSale> {

    boolean existsByInvoiceNumberIgnoreCase(String invoiceNumber);

    boolean existsByInvoiceNumberIgnoreCaseAndIdNot(String invoiceNumber, Long id);

    @Query("select lower(s.invoiceNumber) from SolarSale s where lower(s.invoiceNumber) in :invoices")
    Set<String> findExistingInvoices(Collection<String> invoices);

    long countByInstallationStatusNot(InstallationStatus status);

    /** Open installation jobs an agent can pick up, oldest first. */
    List<SolarSale> findByTaskStateAndInstallationStatusNotOrderByPurchaseDateAscIdAsc(TaskState state,
                                                                                      InstallationStatus status);

    List<SolarSale> findByAgentIdOrderByAssignedAtDescIdDesc(Long agentId);

    /** Atomic "first come, first served" pick-up: 1 if this agent got the task, 0 if it was no longer open. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update SolarSale s set s.agentId = :agentId, s.agentName = :agentName,
                   s.taskState = com.solar.sales.entity.TaskState.ACCEPTED, s.assignedAt = :now, s.acceptedAt = :now
            where s.id = :id and s.taskState = com.solar.sales.entity.TaskState.OPEN
              and s.installationStatus <> com.solar.sales.entity.InstallationStatus.INSTALLED""")
    int pickIfOpen(Long id, Long agentId, String agentName, java.time.LocalDateTime now);

    /** Admin dispatch board: not-yet-installed jobs in one task state, newest records first, optional search. */
    @Query("""
            select s from SolarSale s
            where s.taskState = :state and s.installationStatus <> com.solar.sales.entity.InstallationStatus.INSTALLED
              and (:q is null or lower(s.customerName) like :q or lower(s.invoiceNumber) like :q
                   or lower(s.city) like :q or lower(s.agentName) like :q)
            order by s.createdAt desc, s.id desc""")
    List<SolarSale> board(TaskState state, String q, org.springframework.data.domain.Pageable page);

    long countByTaskStateAndInstallationStatusNot(TaskState state, InstallationStatus status);

    /** [agentId, taskState, installationStatus, count] */
    @Query("""
            select s.agentId, s.taskState, s.installationStatus, count(s) from SolarSale s
            where s.agentId is not null group by s.agentId, s.taskState, s.installationStatus""")
    List<Object[]> agentWorkload();

    List<SolarSale> findTop5ByOrderByPurchaseDateDescIdDesc();

    List<SolarSale> findByPurchaseDateGreaterThanEqual(LocalDate from);

    /** [count, sum(total), sum(paid), sum(capacity)] */
    @Query("""
            select count(s), coalesce(sum(s.totalAmount), 0), coalesce(sum(s.amountPaid), 0),
                   coalesce(sum(s.systemCapacityKw), 0)
            from SolarSale s""")
    List<Object[]> totals();

    @Query("""
            select coalesce(s.panelType, 'Unspecified'), count(s), coalesce(sum(s.systemCapacityKw), 0)
            from SolarSale s group by s.panelType order by count(s) desc""")
    List<Object[]> breakdownByPanelType();

    @Query("""
            select s.installationStatus, count(s), coalesce(sum(s.totalAmount), 0)
            from SolarSale s group by s.installationStatus""")
    List<Object[]> breakdownByInstallationStatus();
}
