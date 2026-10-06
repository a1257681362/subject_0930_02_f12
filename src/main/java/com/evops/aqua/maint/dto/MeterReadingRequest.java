package com.evops.aqua.maint.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 运行小时上报：读数单调不减，回退拒绝。
 */
public class MeterReadingRequest {

    @NotNull(message = "运行小时读数不能为空")
    private BigDecimal readingHours;
    private LocalDateTime readingTime;
    @NotBlank(message = "上报人不能为空")
    private String reporter;
    @NotBlank(message = "requestId 不能为空")
    private String requestId;

    public BigDecimal getReadingHours() { return readingHours; }
    public void setReadingHours(BigDecimal readingHours) { this.readingHours = readingHours; }
    public LocalDateTime getReadingTime() { return readingTime; }
    public void setReadingTime(LocalDateTime readingTime) { this.readingTime = readingTime; }
    public String getReporter() { return reporter; }
    public void setReporter(String reporter) { this.reporter = reporter; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
