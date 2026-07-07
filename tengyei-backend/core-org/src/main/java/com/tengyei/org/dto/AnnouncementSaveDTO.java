package com.tengyei.org.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AnnouncementSaveDTO {
    private Long id;
    @NotBlank(message = "标题不能为空")
    private String title;
    private String content;
    private String level;
    private String targetScope;
    private List<Long> targetIds;
    private String audienceType;
    private List<Long> audienceIds;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Integer status;
}
