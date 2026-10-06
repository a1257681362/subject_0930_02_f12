package com.evops.aqua.maint.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 手动触发计划生成工单（定时扫描之外的人工触发）。同设备同模板已有未关闭工单时拒绝。
 */
public class OrderGenerateRequest {

    @NotNull(message = "计划id不能为空")
    private Long planId;
    @NotBlank(message = "操作人不能为空")
    private String operator;
    private String requestId;

    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
