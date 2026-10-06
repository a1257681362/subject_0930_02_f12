package com.evops.aqua.maint.dto;

import javax.validation.constraints.NotBlank;

/**
 * 换件登记：旧件序列号必填（永久保留），新件序列号必须在库；旧件处置 RECYCLED/DISPOSED。
 */
public class PartReplaceRequest {

    @NotBlank(message = "备件名称不能为空")
    private String partName;
    @NotBlank(message = "旧件序列号不能为空")
    private String oldSerialNo;
    @NotBlank(message = "新件序列号不能为空")
    private String newSerialNo;
    private String oldDisposition;
    @NotBlank(message = "更换人不能为空")
    private String replacedBy;
    private String requestId;

    public String getPartName() { return partName; }
    public void setPartName(String partName) { this.partName = partName; }
    public String getOldSerialNo() { return oldSerialNo; }
    public void setOldSerialNo(String oldSerialNo) { this.oldSerialNo = oldSerialNo; }
    public String getNewSerialNo() { return newSerialNo; }
    public void setNewSerialNo(String newSerialNo) { this.newSerialNo = newSerialNo; }
    public String getOldDisposition() { return oldDisposition; }
    public void setOldDisposition(String oldDisposition) { this.oldDisposition = oldDisposition; }
    public String getReplacedBy() { return replacedBy; }
    public void setReplacedBy(String replacedBy) { this.replacedBy = replacedBy; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
