package com.solar.sales.repository;

import com.solar.sales.entity.InstallationStatus;
import com.solar.sales.entity.SolarSale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
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
