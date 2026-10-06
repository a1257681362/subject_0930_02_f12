package com.evops.aqua.maint.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 保养工单。状态机：
 * PLANNED → ASSIGNED（关联停机窗口+维修人员）→ IN_PROGRESS（开工，设备转MAINT）
 * → WORK_DONE（完工待验收）→ ACCEPTED（验收通过）→ CLOSED（安全确认后恢复运行并关闭）；
 * WORK_DONE 验收驳回 → REJECTED →（返修后）WORK_DONE；未验收（accepted_flag=FALSE）不能关闭。
 * open_flag=TRUE 未关闭，关闭置 NULL：同设备同模板同时至多一张未关闭工单。
 */
@TableName("t_maint_work_order")
public class MaintWorkOrder extends BaseEntity {

    public static final String STATUS_PLANNED = "PLANNED";
    public static final String STATUS_ASSIGNED = "ASSIGNED";
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_WORK_DONE = "WORK_DONE";
    public static final String STATUS_ACCEPTED = "ACCEPTED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_CLOSED = "CLOSED";

    public static final String PRIORITY_NORMAL = "NORMAL";
    public static final String PRIORITY_HIGH = "HIGH";
    public static final String PRIORITY_URGENT = "URGENT";

    private String orderNo;
    private Long planId;
    private Long deviceId;
    private String taskTemplate;
    private String triggerSource;
    private LocalDateTime dueTime;
    private Long windowId;
    private Long assignedCrewId;
    private String assignedCrewName;
    private String priority;
    private String status;
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Boolean openFlag;
    private BigDecimal meterSnapshot;
    private LocalDateTime startedTime;
    private LocalDateTime completedTime;
    private Boolean acceptedFlag;
    private String acceptedBy;
    private LocalDateTime acceptedTime;
    private String acceptRemark;
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String rejectReason;
    private LocalDateTime closedTime;
    private Integer escalationLevel;
    private LocalDateTime escalatedTime;
    private String requestId;
    private String remark;

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long deviceId) { this.deviceId = deviceId; }
    public String getTaskTemplate() { return taskTemplate; }
    public void setTaskTemplate(String taskTemplate) { this.taskTemplate = taskTemplate; }
    public String getTriggerSource() { return triggerSource; }
    public void setTriggerSource(String triggerSource) { this.triggerSource = triggerSource; }
    public LocalDateTime getDueTime() { return dueTime; }
    public void setDueTime(LocalDateTime dueTime) { this.dueTime = dueTime; }
    public Long getWindowId() { return windowId; }
    public void setWindowId(Long windowId) { this.windowId = windowId; }
    public Long getAssignedCrewId() { return assignedCrewId; }
    public void setAssignedCrewId(Long assignedCrewId) { this.assignedCrewId = assignedCrewId; }
    public String getAssignedCrewName() { return assignedCrewName; }
    public void setAssignedCrewName(String assignedCrewName) { this.assignedCrewName = assignedCrewName; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Boolean getOpenFlag() { return openFlag; }
    public void setOpenFlag(Boolean openFlag) { this.openFlag = openFlag; }
    public BigDecimal getMeterSnapshot() { return meterSnapshot; }
    public void setMeterSnapshot(BigDecimal meterSnapshot) { this.meterSnapshot = meterSnapshot; }
    public LocalDateTime getStartedTime() { return startedTime; }
    public void setStartedTime(LocalDateTime startedTime) { this.startedTime = startedTime; }
    public LocalDateTime getCompletedTime() { return completedTime; }
    public void setCompletedTime(LocalDateTime completedTime) { this.completedTime = completedTime; }
    public Boolean getAcceptedFlag() { return acceptedFlag; }
    public void setAcceptedFlag(Boolean acceptedFlag) { this.acceptedFlag = acceptedFlag; }
    public String getAcceptedBy() { return acceptedBy; }
    public void setAcceptedBy(String acceptedBy) { this.acceptedBy = acceptedBy; }
    public LocalDateTime getAcceptedTime() { return acceptedTime; }
    public void setAcceptedTime(LocalDateTime acceptedTime) { this.acceptedTime = acceptedTime; }
    public String getAcceptRemark() { return acceptRemark; }
    public void setAcceptRemark(String acceptRemark) { this.acceptRemark = acceptRemark; }
    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }
    public LocalDateTime getClosedTime() { return closedTime; }
    public void setClosedTime(LocalDateTime closedTime) { this.closedTime = closedTime; }
    public Integer getEscalationLevel() { return escalationLevel; }
    public void setEscalationLevel(Integer escalationLevel) { this.escalationLevel = escalationLevel; }
    public LocalDateTime getEscalatedTime() { return escalatedTime; }
    public void setEscalatedTime(LocalDateTime escalatedTime) { this.escalatedTime = escalatedTime; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
