package com.evops.aqua.maint.dto;

import javax.validation.constraints.NotBlank;

/**
 * 工单验收：approved=FALSE 驳回必须填写原因；通过后才允许安全确认与关闭。
 */
public class OrderAcceptRequest {

    private Boolean approved;
    @NotBlank(message = "验收人不能为空")
    private String acceptedBy;
    private String remark;
    private String requestId;

    public Boolean getApproved() { return approved; }
    public void setApproved(Boolean approved) { this.approved = approved; }
    public String getAcceptedBy() { return acceptedBy; }
    public void setAcceptedBy(String acceptedBy) { this.acceptedBy = acceptedBy; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
