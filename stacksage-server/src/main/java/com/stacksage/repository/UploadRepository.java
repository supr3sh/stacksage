package com.stacksage.repository;

import com.stacksage.model.UploadRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface UploadRepository extends JpaRepository<UploadRecord, String> {

    List<UploadRecord> findByRetainTrueAndCreatedAtBefore(LocalDateTime cutoff);

    List<UploadRecord> findByCreatedAtBefore(LocalDateTime cutoff);
}
