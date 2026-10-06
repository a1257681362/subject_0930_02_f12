package com.evops.aqua.maint.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDateTime;

/**
 * 备件：按序列号逐件管理，换件只追加换件记录、不改写旧件序列号。
 * IN_STOCK 在库 / INSTALLED 已装机 / REMOVED 已拆下待处置 / RECYCLED 回收 / DISPOSED 报废。
 */
@TableName("t_maint_spare_part")
public class MaintSparePart extends BaseEntity {

    public static final String STATUS_IN_STOCK = "IN_STOCK";
    public static final String STATUS_INSTALLED = "INSTALLED";
    public static final String STATUS_REMOVED = "REMOVED";
    public static final String STATUS_RECYCLED = "RECYCLED";
    public static final String STATUS_DISPOSED = "DISPOSED";

    private String partName;
    private String serialNo;
    private String spec;
    private String status;
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Long installedDeviceId;
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private LocalDateTime installedTime;
    private String requestId;

    public String getPartName() { return partName; }
    public void setPartName(String partName) { this.partName = partName; }
    public String getSerialNo() { return serialNo; }
    public void setSerialNo(String serialNo) { this.serialNo = serialNo; }
    public String getSpec() { return spec; }
    public void setSpec(String spec) { this.spec = spec; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getInstalledDeviceId() { return installedDeviceId; }
    public void setInstalledDeviceId(Long installedDeviceId) { this.installedDeviceId = installedDeviceId; }
    public LocalDateTime getInstalledTime() { return installedTime; }
    public void setInstalledTime(LocalDateTime installedTime) { this.installedTime = installedTime; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
