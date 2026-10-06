package com.evops.aqua.maint.dto;

import javax.validation.constraints.NotBlank;

/**
 * 工单完工：所有已登记备件需求必须完成换件，完工后进入待验收。
 */
public class OrderCompleteRequest {

    @NotBlank(message = "操作人不能为空")
    private String operator;
    private String requestId;
    private String remark;

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
