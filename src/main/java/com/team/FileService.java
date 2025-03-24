package com.team;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

@Service
public class FileService {

    private static final String RELATIVE_UPLOAD_PATH = "src/main/resources/static/img/upload/";

    public String saveImage(MultipartFile file) {
        if (file.isEmpty()) {
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
            String extension = originalFileName.substring(originalFileName.lastIndexOf("."));
            String newFileName = UUID.randomUUID() + "_" + originalFileName;

            // 실제 저장 경로
            Path filePath = uploadDir.resolve(newFileName);
            file.transferTo(filePath.toFile());

            // 웹에서 접근 가능한 경로 반환
            return "/img/upload/" + newFileName;

        } catch (IOException e) {
            throw new RuntimeException("이미지 저장 실패", e);
        }
    }
}
