package com.evops.aqua.maint.dto;

import javax.validation.constraints.NotBlank;

/**
 * 备件入库：序列号逐件管理，同序列号全库唯一。
 */
public class SparePartInRequest {

    @NotBlank(message = "备件名称不能为空")
    private String partName;
    @NotBlank(message = "备件序列号不能为空")
    private String serialNo;
    private String spec;
    @NotBlank(message = "操作人不能为空")
    private String operator;
    private String requestId;

    public String getPartName() { return partName; }
    public void setPartName(String partName) { this.partName = partName; }
    public String getSerialNo() { return serialNo; }
    public void setSerialNo(String serialNo) { this.serialNo = serialNo; }
    public String getSpec() { return spec; }
    public void setSpec(String spec) { this.spec = spec; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
