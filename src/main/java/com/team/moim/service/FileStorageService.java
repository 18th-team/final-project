package com.team.moim.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class FileStorageService {

    private static final String RELATIVE_UPLOAD_PATH = "src/main/resources/static/img/user/";

    public String saveImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }

        try {
            // 저장 경로 객체화
            Path uploadDir = Paths.get(RELATIVE_UPLOAD_PATH).toAbsolutePath();

            // 폴더 없으면 생성
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }

            // 고유한 파일 이름 생성
            String originalFileName = file.getOriginalFilename();
            if (originalFileName == null || originalFileName.isEmpty()) {
                throw new IllegalArgumentException("파일 이름이 유효하지 않습니다");
            }
            String extension = originalFileName.substring(originalFileName.lastIndexOf("."));
            String newFileName = UUID.randomUUID() + "_" + originalFileName;

            // 실제 저장 경로
            Path filePath = uploadDir.resolve(newFileName);
            file.transferTo(filePath.toFile());

            // 뷰에서 접근 가능한 경로 반환
            return "/upload/" + newFileName;

        } catch (IOException e) {
            throw new RuntimeException("이미지 저장 실패: " + e.getMessage(), e);
        }
    }
}