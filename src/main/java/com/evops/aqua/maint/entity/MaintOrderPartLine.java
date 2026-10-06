package com.evops.aqua.maint.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

/**
 * 工单备件需求行：备件名 + 换件后登记的新/旧序列号快照。
 * 权威的旧件谱系在 t_maint_part_replacement（只追加）。
 */
@TableName("t_maint_order_part_line")
public class MaintOrderPartLine extends BaseEntity {

    private Long orderId;
    private String partName;
    private String newSerialNo;
    private String oldSerialNo;
    private Boolean replacedFlag;

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getPartName() { return partName; }
    public void setPartName(String partName) { this.partName = partName; }
    public String getNewSerialNo() { return newSerialNo; }
    public void setNewSerialNo(String newSerialNo) { this.newSerialNo = newSerialNo; }
    public String getOldSerialNo() { return oldSerialNo; }
    public void setOldSerialNo(String oldSerialNo) { this.oldSerialNo = oldSerialNo; }
    public Boolean getReplacedFlag() { return replacedFlag; }
    public void setReplacedFlag(Boolean replacedFlag) { this.replacedFlag = replacedFlag; }
}
