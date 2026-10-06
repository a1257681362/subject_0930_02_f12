package com.evops.aqua.maint.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 安全确认：断电隔离/上锁挂牌/复检三项必须全部为 TRUE，否则拒绝恢复运行。
 */
public class SafetyConfirmRequest {

    @NotNull(message = "断电隔离确认不能为空")
    private Boolean powerIsolatedFlag;
    @NotNull(message = "上锁挂牌确认不能为空")
    private Boolean lockoutTagoutFlag;
    @NotNull(message = "复检结果不能为空")
    private Boolean retestOkFlag;
    @NotBlank(message = "确认人不能为空")
    private String confirmedBy;
    private String requestId;

    public Boolean getPowerIsolatedFlag() { return powerIsolatedFlag; }
    public void setPowerIsolatedFlag(Boolean powerIsolatedFlag) { this.powerIsolatedFlag = powerIsolatedFlag; }
    public Boolean getLockoutTagoutFlag() { return lockoutTagoutFlag; }
    public void setLockoutTagoutFlag(Boolean lockoutTagoutFlag) { this.lockoutTagoutFlag = lockoutTagoutFlag; }
    public Boolean getRetestOkFlag() { return retestOkFlag; }
    public void setRetestOkFlag(Boolean retestOkFlag) { this.retestOkFlag = retestOkFlag; }
    public String getConfirmedBy() { return confirmedBy; }
    public void setConfirmedBy(String confirmedBy) { this.confirmedBy = confirmedBy; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
