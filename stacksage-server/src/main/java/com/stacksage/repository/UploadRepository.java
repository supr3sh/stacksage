package com.stacksage.repository;

import com.stacksage.model.UploadRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UploadRepository extends JpaRepository<UploadRecord, String> {
}
