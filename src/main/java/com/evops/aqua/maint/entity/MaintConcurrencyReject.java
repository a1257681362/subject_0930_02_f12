package com.evops.aqua.maint.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDateTime;

/**
 * 并发拒单审计：被拒请求以独立事务落库（业务事务回滚不影响留痕），记载谁生效、谁被拒、原因。
 */
@TableName("t_maint_concurrency_reject")
public class MaintConcurrencyReject extends BaseEntity {

    private String operation;
    private String targetType;
    private String targetId;
    private String winnerRequestId;
    private String winnerOperator;
    private String loserOperator;
    private String loserRequestId;
    private String reason;
    private LocalDateTime rejectedTime;

    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }
    public String getWinnerRequestId() { return winnerRequestId; }
    public void setWinnerRequestId(String winnerRequestId) { this.winnerRequestId = winnerRequestId; }
    public String getWinnerOperator() { return winnerOperator; }
    public void setWinnerOperator(String winnerOperator) { this.winnerOperator = winnerOperator; }
    public String getLoserOperator() { return loserOperator; }
    public void setLoserOperator(String loserOperator) { this.loserOperator = loserOperator; }
    public String getLoserRequestId() { return loserRequestId; }
    public void setLoserRequestId(String loserRequestId) { this.loserRequestId = loserRequestId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public LocalDateTime getRejectedTime() { return rejectedTime; }
    public void setRejectedTime(LocalDateTime rejectedTime) { this.rejectedTime = rejectedTime; }
}
