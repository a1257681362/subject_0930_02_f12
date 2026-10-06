package com.evops.aqua.maint.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

/**
 * 保养模块变更证据：只追加不修改不删除（对象、动作、操作前后 JSON 快照、操作者、幂等请求号）。
 */
@TableName("t_maint_change_evidence")
public class MaintChangeEvidence extends BaseEntity {

    public static final String ENTITY_DEVICE = "DEVICE";
    public static final String ENTITY_METER = "METER_READING";
    public static final String ENTITY_PLAN = "PLAN";
    public static final String ENTITY_WINDOW = "WINDOW";
    public static final String ENTITY_CREW = "CREW";
    public static final String ENTITY_PART = "SPARE_PART";
    public static final String ENTITY_ORDER = "WORK_ORDER";
    public static final String ENTITY_PART_LINE = "ORDER_PART_LINE";
    public static final String ENTITY_REPLACEMENT = "PART_REPLACEMENT";
    public static final String ENTITY_SAFETY = "SAFETY_CHECK";

    public static final String ACTION_CREATE = "CREATE";
    public static final String ACTION_UPDATE = "UPDATE";
    public static final String ACTION_GENERATE = "GENERATE";
    public static final String ACTION_ASSIGN = "ASSIGN";
    public static final String ACTION_START = "START";
    public static final String ACTION_REPLACE_PART = "REPLACE_PART";
    public static final String ACTION_COMPLETE = "COMPLETE";
    public static final String ACTION_ACCEPT = "ACCEPT";
    public static final String ACTION_REJECT = "REJECT";
    public static final String ACTION_SAFETY_CONFIRM = "SAFETY_CONFIRM";
    public static final String ACTION_RESUME = "RESUME";
    public static final String ACTION_CLOSE = "CLOSE";
    public static final String ACTION_CANCEL = "CANCEL";
    public static final String ACTION_ESCALATE = "ESCALATE";
    public static final String ACTION_CONCURRENT_REJECT = "CONCURRENT_REJECT";

    private String entityType;
    private Long entityId;
    private String action;
    private String requestId;
    private String operator;
    private String beforeJson;
    private String afterJson;
    private String detail;

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public Long getEntityId() { return entityId; }
    public void setEntityId(Long entityId) { this.entityId = entityId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getBeforeJson() { return beforeJson; }
    public void setBeforeJson(String beforeJson) { this.beforeJson = beforeJson; }
    public String getAfterJson() { return afterJson; }
    public void setAfterJson(String afterJson) { this.afterJson = afterJson; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
}
