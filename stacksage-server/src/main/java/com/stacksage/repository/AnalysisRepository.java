package com.stacksage.repository;

import com.stacksage.model.AnalysisRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AnalysisRepository extends JpaRepository<AnalysisRecord, String> {

    Optional<AnalysisRecord> findTopByUploadIdOrderByCreatedAtDesc(String uploadId);
}
