package com.evops.aqua.maint.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 运行小时上报流水：只追加，作为按小时触发计划的累计依据。
 */
@TableName("t_maint_meter_reading")
public class MaintMeterReading extends BaseEntity {

    private Long deviceId;
    private BigDecimal readingHours;
    private LocalDateTime readingTime;
    private String reporter;
    private String requestId;

    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long deviceId) { this.deviceId = deviceId; }
    public BigDecimal getReadingHours() { return readingHours; }
    public void setReadingHours(BigDecimal readingHours) { this.readingHours = readingHours; }
    public LocalDateTime getReadingTime() { return readingTime; }
    public void setReadingTime(LocalDateTime readingTime) { this.readingTime = readingTime; }
    public String getReporter() { return reporter; }
    public void setReporter(String reporter) { this.reporter = reporter; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
