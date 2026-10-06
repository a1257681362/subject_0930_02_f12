package com.evops.aqua.maint.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDateTime;

/**
 * 并发生效占位：业务事务开始前以独立事务写入（active_flag=TRUE），业务结束释放（置NULL）。
 * 唯一键 (operation, target_type, target_id, active_flag) 保证同操作同对象并发只一生效。
 */
@TableName("t_maint_op_claim")
public class MaintOpClaim extends BaseEntity {

    private String operation;
    private String targetType;
    private String targetId;
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Boolean activeFlag;
    private String holderRequestId;
    private String holderOperator;
    private LocalDateTime claimTime;
    private LocalDateTime releasedTime;

    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }
    public Boolean getActiveFlag() { return activeFlag; }
    public void setActiveFlag(Boolean activeFlag) { this.activeFlag = activeFlag; }
    public String getHolderRequestId() { return holderRequestId; }
    public void setHolderRequestId(String holderRequestId) { this.holderRequestId = holderRequestId; }
    public String getHolderOperator() { return holderOperator; }
    public void setHolderOperator(String holderOperator) { this.holderOperator = holderOperator; }
    public LocalDateTime getClaimTime() { return claimTime; }
    public void setClaimTime(LocalDateTime claimTime) { this.claimTime = claimTime; }
    public LocalDateTime getReleasedTime() { return releasedTime; }
    public void setReleasedTime(LocalDateTime releasedTime) { this.releasedTime = releasedTime; }
}
