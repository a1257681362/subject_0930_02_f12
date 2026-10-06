package com.evops.aqua.maint.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDateTime;

/**
 * 换件记录：只追加。旧件序列号永久保留，关联新件序列号与处置方式（RECYCLED/DISPOSED）。
 */
@TableName("t_maint_part_replacement")
public class MaintPartReplacement extends BaseEntity {

    public static final String DISPOSITION_RECYCLED = "RECYCLED";
    public static final String DISPOSITION_DISPOSED = "DISPOSED";

    private Long orderId;
    private Long deviceId;
    private String partName;
    private String oldSerialNo;
    private String newSerialNo;
    private Long oldPartId;
    private Long newPartId;
    private String oldDisposition;
    private String replacedBy;
    private LocalDateTime replacedTime;
    private String requestId;

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long deviceId) { this.deviceId = deviceId; }
    public String getPartName() { return partName; }
    public void setPartName(String partName) { this.partName = partName; }
    public String getOldSerialNo() { return oldSerialNo; }
    public void setOldSerialNo(String oldSerialNo) { this.oldSerialNo = oldSerialNo; }
    public String getNewSerialNo() { return newSerialNo; }
    public void setNewSerialNo(String newSerialNo) { this.newSerialNo = newSerialNo; }
    public Long getOldPartId() { return oldPartId; }
    public void setOldPartId(Long oldPartId) { this.oldPartId = oldPartId; }
    public Long getNewPartId() { return newPartId; }
    public void setNewPartId(Long newPartId) { this.newPartId = newPartId; }
    public String getOldDisposition() { return oldDisposition; }
    public void setOldDisposition(String oldDisposition) { this.oldDisposition = oldDisposition; }
    public String getReplacedBy() { return replacedBy; }
    public void setReplacedBy(String replacedBy) { this.replacedBy = replacedBy; }
    public LocalDateTime getReplacedTime() { return replacedTime; }
    public void setReplacedTime(LocalDateTime replacedTime) { this.replacedTime = replacedTime; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
