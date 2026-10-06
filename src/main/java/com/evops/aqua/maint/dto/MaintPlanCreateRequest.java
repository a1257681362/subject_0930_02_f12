package com.evops.aqua.maint.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 创建保养计划：HOURS 必填 intervalHours/leadHours；CALENDAR 必填 intervalDays/leadDays。
 */
public class MaintPlanCreateRequest {

    @NotBlank(message = "计划编号不能为空")
    private String planNo;
    @NotNull(message = "设备id不能为空")
    private Long deviceId;
    @NotBlank(message = "保养项目模板不能为空")
    private String taskTemplate;
    @NotBlank(message = "触发类型不能为空（HOURS/CALENDAR）")
    private String triggerType;
    private BigDecimal intervalHours;
    private Integer intervalDays;
    private BigDecimal leadHours;
    private Integer leadDays;
    @NotNull(message = "计划开始日期不能为空")
    private LocalDate startDate;
    private LocalDate endDate;
    private List<String> partNames;
    private String operator;
    private String requestId;
    private String remark;

    public String getPlanNo() { return planNo; }
    public void setPlanNo(String planNo) { this.planNo = planNo; }
    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long deviceId) { this.deviceId = deviceId; }
    public String getTaskTemplate() { return taskTemplate; }
    public void setTaskTemplate(String taskTemplate) { this.taskTemplate = taskTemplate; }
    public String getTriggerType() { return triggerType; }
    public void setTriggerType(String triggerType) { this.triggerType = triggerType; }
    public BigDecimal getIntervalHours() { return intervalHours; }
    public void setIntervalHours(BigDecimal intervalHours) { this.intervalHours = intervalHours; }
    public Integer getIntervalDays() { return intervalDays; }
    public void setIntervalDays(Integer intervalDays) { this.intervalDays = intervalDays; }
    public BigDecimal getLeadHours() { return leadHours; }
    public void setLeadHours(BigDecimal leadHours) { this.leadHours = leadHours; }
    public Integer getLeadDays() { return leadDays; }
    public void setLeadDays(Integer leadDays) { this.leadDays = leadDays; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public List<String> getPartNames() { return partNames; }
    public void setPartNames(List<String> partNames) { this.partNames = partNames; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
