package com.evops.aqua.maint.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 保养计划：HOURS 按运行小时触发 / CALENDAR 按日历周期触发。
 * 计划调整（间隔/提前量）只改后续排期（next_due_*），已生成工单不受影响。
 * active_flag=TRUE 生效、取消置 NULL，同设备同模板至多一个生效计划。
 */
@TableName("t_maint_plan")
public class MaintPlan extends BaseEntity {

    public static final String TRIGGER_HOURS = "HOURS";
    public static final String TRIGGER_CALENDAR = "CALENDAR";

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_CANCELLED = "CANCELLED";

    private String planNo;
    private Long deviceId;
    private String taskTemplate;
    private String triggerType;
    private BigDecimal intervalHours;
    private Integer intervalDays;
    private BigDecimal leadHours;
    private Integer leadDays;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Boolean activeFlag;
    private BigDecimal anchorMeter;
    private LocalDate anchorDate;
    private BigDecimal nextDueMeter;
    private LocalDate nextDueDate;
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String lastGeneratedKey;
    private Integer dueCounter;
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
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public BigDecimal getAnchorMeter() { return anchorMeter; }
    public void setAnchorMeter(BigDecimal anchorMeter) { this.anchorMeter = anchorMeter; }
    public LocalDate getAnchorDate() { return anchorDate; }
    public void setAnchorDate(LocalDate anchorDate) { this.anchorDate = anchorDate; }
    public Boolean getActiveFlag() { return activeFlag; }
    public void setActiveFlag(Boolean activeFlag) { this.activeFlag = activeFlag; }
    public BigDecimal getNextDueMeter() { return nextDueMeter; }
    public void setNextDueMeter(BigDecimal nextDueMeter) { this.nextDueMeter = nextDueMeter; }
    public LocalDate getNextDueDate() { return nextDueDate; }
    public void setNextDueDate(LocalDate nextDueDate) { this.nextDueDate = nextDueDate; }
    public String getLastGeneratedKey() { return lastGeneratedKey; }
    public void setLastGeneratedKey(String lastGeneratedKey) { this.lastGeneratedKey = lastGeneratedKey; }
    public Integer getDueCounter() { return dueCounter; }
    public void setDueCounter(Integer dueCounter) { this.dueCounter = dueCounter; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
