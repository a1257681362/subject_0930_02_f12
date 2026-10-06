package com.evops.aqua.maint.dto;

import javax.validation.constraints.NotBlank;

/**
 * 工单关闭（恢复运行）：必须已验收通过且安全确认已完成，否则拒绝。
 */
public class OrderCloseRequest {

    @NotBlank(message = "操作人不能为空")
    private String operator;
    private String requestId;

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
