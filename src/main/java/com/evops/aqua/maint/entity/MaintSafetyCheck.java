package com.evops.aqua.maint.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDateTime;

/**
 * 安全确认单：恢复运行前必须完成且断电隔离/上锁挂牌/复检三项全部通过。
 */
@TableName("t_maint_safety_check")
public class MaintSafetyCheck extends BaseEntity {

    private Long orderId;
    private Long deviceId;
    private Boolean powerIsolatedFlag;
    private Boolean lockoutTagoutFlag;
    private Boolean retestOkFlag;
    private String confirmedBy;
    private LocalDateTime confirmedTime;
    private String requestId;

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long deviceId) { this.deviceId = deviceId; }
    public Boolean getPowerIsolatedFlag() { return powerIsolatedFlag; }
    public void setPowerIsolatedFlag(Boolean powerIsolatedFlag) { this.powerIsolatedFlag = powerIsolatedFlag; }
    public Boolean getLockoutTagoutFlag() { return lockoutTagoutFlag; }
    public void setLockoutTagoutFlag(Boolean lockoutTagoutFlag) { this.lockoutTagoutFlag = lockoutTagoutFlag; }
    public Boolean getRetestOkFlag() { return retestOkFlag; }
    public void setRetestOkFlag(Boolean retestOkFlag) { this.retestOkFlag = retestOkFlag; }
    public String getConfirmedBy() { return confirmedBy; }
    public void setConfirmedBy(String confirmedBy) { this.confirmedBy = confirmedBy; }
    public LocalDateTime getConfirmedTime() { return confirmedTime; }
    public void setConfirmedTime(LocalDateTime confirmedTime) { this.confirmedTime = confirmedTime; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
