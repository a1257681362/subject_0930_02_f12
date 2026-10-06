package com.evops.aqua.maint.dto;

import javax.validation.constraints.NotBlank;

/**
 * 维修人员登记。
 */
public class CrewCreateRequest {

    @NotBlank(message = "人员编码不能为空")
    private String crewCode;
    @NotBlank(message = "人员姓名不能为空")
    private String crewName;
    private String skill;
    private String operator;
    private String requestId;

    public String getCrewCode() { return crewCode; }
    public void setCrewCode(String crewCode) { this.crewCode = crewCode; }
    public String getCrewName() { return crewName; }
    public void setCrewName(String crewName) { this.crewName = crewName; }
    public String getSkill() { return skill; }
    public void setSkill(String skill) { this.skill = skill; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
