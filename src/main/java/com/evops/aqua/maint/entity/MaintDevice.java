package com.evops.aqua.maint.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 养殖设备（增氧机/泵站/投饵机）：运行小时表读数随上报累计，
 * 设备在保养开工时转 MAINT，安全确认通过后才能恢复 RUNNING。
 */
@TableName("t_maint_device")
public class MaintDevice extends BaseEntity {

    public static final String TYPE_AERATOR = "AERATOR";
    public static final String TYPE_PUMP_STATION = "PUMP_STATION";
    public static final String TYPE_FEEDER = "FEEDER";

    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_MAINT = "MAINT";
    public static final String STATUS_STOPPED = "STOPPED";

    private String deviceCode;
    private String deviceName;
    private String deviceType;
    private Long pondId;
    private String status;
    private BigDecimal meterHours;
    private BigDecimal lastMaintainMeter;
    private LocalDate lastMaintainDate;
    private String requestId;
    private String remark;

    public String getDeviceCode() { return deviceCode; }
    public void setDeviceCode(String deviceCode) { this.deviceCode = deviceCode; }
    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }
    public String getDeviceType() { return deviceType; }
    public void setDeviceType(String deviceType) { this.deviceType = deviceType; }
    public Long getPondId() { return pondId; }
    public void setPondId(Long pondId) { this.pondId = pondId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public BigDecimal getMeterHours() { return meterHours; }
    public void setMeterHours(BigDecimal meterHours) { this.meterHours = meterHours; }
    public BigDecimal getLastMaintainMeter() { return lastMaintainMeter; }
    public void setLastMaintainMeter(BigDecimal lastMaintainMeter) { this.lastMaintainMeter = lastMaintainMeter; }
    public LocalDate getLastMaintainDate() { return lastMaintainDate; }
    public void setLastMaintainDate(LocalDate lastMaintainDate) { this.lastMaintainDate = lastMaintainDate; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
