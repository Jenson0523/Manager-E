package com.tengyei.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/** /uploads/** 由后端直接可服务(生产 nginx 优先,开发/兜底走这里) */
@Configuration
public class UploadResourceConfig implements WebMvcConfigurer {

    @Value("${upload.path:/opt/tengyei/uploads}")
    private String uploadPath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + Paths.get(uploadPath).toAbsolutePath() + "/");
    }
}
