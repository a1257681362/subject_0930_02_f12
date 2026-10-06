package com.evops.aqua.maint.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 工单分派：关联停机窗口与维修人员。
 */
public class OrderAssignRequest {

    @NotNull(message = "停机窗口id不能为空")
    private Long windowId;
    @NotNull(message = "维修人员id不能为空")
    private Long crewId;
    @NotBlank(message = "操作人不能为空")
    private String operator;
    private String requestId;

    public Long getWindowId() { return windowId; }
    public void setWindowId(Long windowId) { this.windowId = windowId; }
    public Long getCrewId() { return crewId; }
    public void setCrewId(Long crewId) { this.crewId = crewId; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
