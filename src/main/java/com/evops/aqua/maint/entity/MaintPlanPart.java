package com.evops.aqua.maint.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

/**
 * 计划备件模板：生成工单时复制为工单备件需求行。
 */
@TableName("t_maint_plan_part")
public class MaintPlanPart extends BaseEntity {

    private Long planId;
    private String partName;

    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
    public String getPartName() { return partName; }
    public void setPartName(String partName) { this.partName = partName; }
}
