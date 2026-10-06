package com.evops.aqua.maint.dto;

import javax.validation.constraints.NotBlank;
import java.math.BigDecimal;

/**
 * 调整计划：只改间隔/提前量并据此重算后续排期；已生成工单不受影响。
 */
public class PlanAdjustRequest {

    private BigDecimal intervalHours;
    private Integer intervalDays;
    private BigDecimal leadHours;
    private Integer leadDays;
    @NotBlank(message = "操作人不能为空")
    private String operator;
    private String requestId;
    private String remark;

    public BigDecimal getIntervalHours() { return intervalHours; }
    public void setIntervalHours(BigDecimal intervalHours) { this.intervalHours = intervalHours; }
    public Integer getIntervalDays() { return intervalDays; }
    public void setIntervalDays(Integer intervalDays) { this.intervalDays = intervalDays; }
    public BigDecimal getLeadHours() { return leadHours; }
    public void setLeadHours(BigDecimal leadHours) { this.leadHours = leadHours; }
    public Integer getLeadDays() { return leadDays; }
    public void setLeadDays(Integer leadDays) { this.leadDays = leadDays; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
