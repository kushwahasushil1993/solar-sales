package com.solar.sales.repository;

import com.solar.sales.entity.UploadBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UploadBatchRepository extends JpaRepository<UploadBatch, Long> {

    List<UploadBatch> findTop20ByOrderByUploadedAtDesc();
}
