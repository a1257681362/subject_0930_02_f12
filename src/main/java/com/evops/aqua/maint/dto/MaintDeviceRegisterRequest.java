package com.evops.aqua.maint.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * 设备登记：增氧机/泵站/投饵机建档。
 */
public class MaintDeviceRegisterRequest {

    @NotBlank(message = "设备编码不能为空")
    private String deviceCode;
    @NotBlank(message = "设备名称不能为空")
    private String deviceName;
    @NotBlank(message = "设备类型不能为空")
    private String deviceType;
    private Long pondId;
    @NotNull(message = "上次保养日期不能为空")
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
    public LocalDate getLastMaintainDate() { return lastMaintainDate; }
    public void setLastMaintainDate(LocalDate lastMaintainDate) { this.lastMaintainDate = lastMaintainDate; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
