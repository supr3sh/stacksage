package com.stacksage.service;

import com.stacksage.model.UploadResponse;
import org.springframework.web.multipart.MultipartFile;

public interface FileUploadService {

    UploadResponse uploadFile(MultipartFile file, boolean retain);

    UploadResponse getUpload(String id);

    UploadResponse getUploadWithContent(String id);

    void deleteUpload(String id);
}
