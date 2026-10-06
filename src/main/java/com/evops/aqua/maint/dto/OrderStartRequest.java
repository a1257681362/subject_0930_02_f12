package com.evops.aqua.maint.dto;

import javax.validation.constraints.NotBlank;

/**
 * 工单开工：设备转入保养停机（MAINT）。
 */
public class OrderStartRequest {

    @NotBlank(message = "操作人不能为空")
    private String operator;
    private String requestId;

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
