package com.tengyei.org.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class ContractSaveDTO {
    private Long id;
    /** 空则由服务端按 HT{yyyyMM}{序号} 生成 */
    private String contractNo;
    @NotBlank(message = "合同名称不能为空")
    private String name;
    private String type;
    @NotBlank(message = "对方单位不能为空")
    private String partyB;
    private String partyBContact;
    private String partyBPhone;
    private BigDecimal amount;
    private LocalDate signDate;
    private LocalDate startDate;
    private LocalDate endDate;
    private Long ownerId;
    private String status;
    private List<Attachment> attachments;
    private String remark;

    @Data
    public static class Attachment {
        private String name;
        private String url;
    }
}
