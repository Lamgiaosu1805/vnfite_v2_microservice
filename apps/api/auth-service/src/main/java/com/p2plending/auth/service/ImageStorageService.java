package com.p2plending.auth.service;

import org.springframework.web.multipart.MultipartFile;

public interface ImageStorageService {

    /**
     * Upload ảnh lên hệ thống lưu trữ bên ngoài.
     * @param file ảnh upload từ client
     * @return ID string do hệ thống bên ngoài trả về
     */
    String upload(MultipartFile file);
}
