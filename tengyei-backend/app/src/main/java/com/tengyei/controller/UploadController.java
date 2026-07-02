package com.tengyei.controller;

import com.tengyei.common.response.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class UploadController {

    @Value("${upload.path:/opt/tengyei/uploads}")
    private String uploadPath;

    @PostMapping("/upload/logo")
    public Result<String> uploadLogo(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return Result.fail(422, "上传文件不能为空");
        }

        String originalName = file.getOriginalFilename();
        if (originalName == null) {
            return Result.fail(422, "文件名无效");
        }

        String ext = originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase();
        if (!ext.matches("jpg|jpeg|png|gif|webp|svg")) {
            return Result.fail(422, "仅支持 jpg/png/gif/webp/svg 格式");
        }

        // Max 2MB
        if (file.getSize() > 2 * 1024 * 1024) {
            return Result.fail(422, "文件大小不能超过 2MB");
        }

        try {
            Path dir = Paths.get(uploadPath, "logo");
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }

            String fileName = UUID.randomUUID().toString().replace("-", "") + "." + ext;
            Path target = dir.resolve(fileName);
            file.transferTo(target.toFile());

            // Return relative URL path for nginx static serving
            String url = "/uploads/logo/" + fileName;
            log.info("Logo uploaded: {}", url);
            return Result.ok(url);
        } catch (IOException e) {
            log.error("Logo upload failed", e);
            return Result.fail(500, "文件上传失败");
        }
    }
}
