package com.evops.aqua.maint.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * 开放停机窗口：同设备时间重叠的窗口并发开放只一生效。
 */
public class WindowCreateRequest {

    @NotBlank(message = "窗口编号不能为空")
    private String windowNo;
    @NotNull(message = "设备id不能为空")
    private Long deviceId;
    @NotNull(message = "窗口开始时间不能为空")
    private LocalDateTime startTime;
    @NotNull(message = "窗口结束时间不能为空")
    private LocalDateTime endTime;
    @NotBlank(message = "操作人不能为空")
    private String operator;
    private String requestId;
    private String remark;

    public String getWindowNo() { return windowNo; }
    public void setWindowNo(String windowNo) { this.windowNo = windowNo; }
    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long deviceId) { this.deviceId = deviceId; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
