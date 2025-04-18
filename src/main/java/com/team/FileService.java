package com.team;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

@Service
public class FileService {

    private static final String USER_UPLOAD_PATH = "src/main/resources/static/img/user/";
    private static final String POST_UPLOAD_PATH = "src/main/resources/static/img/upload/";

    // 프로필 이미지 저장
    public String saveProfileImage(MultipartFile file) {
        return saveImageTo(file, USER_UPLOAD_PATH, "/img/user/");
    }

    // 게시물 이미지 저장
    public String savePostImage(MultipartFile file) {
        return saveImageTo(file, POST_UPLOAD_PATH, "/img/upload/");
    }

    // 공통 저장 로직
    private String saveImageTo(MultipartFile file, String saveDir, String returnPathPrefix) {
        if (file.isEmpty()) return null;

        try {
            Path uploadDir = Paths.get(saveDir).toAbsolutePath();

            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }

            String originalFileName = file.getOriginalFilename();
            String newFileName = UUID.randomUUID() + "_" + originalFileName;

            Path filePath = uploadDir.resolve(newFileName);
            file.transferTo(filePath.toFile());

            return returnPathPrefix + newFileName;

        } catch (IOException e) {
            throw new RuntimeException("이미지 저장 실패", e);
        }
    }
}
