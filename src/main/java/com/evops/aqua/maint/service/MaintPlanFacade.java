package com.evops.aqua.maint.service;

import com.evops.aqua.maint.dto.PlanAdjustRequest;
import com.evops.aqua.maint.dto.MaintPlanCreateRequest;
import com.evops.aqua.maint.entity.MaintPlan;
import org.springframework.stereotype.Component;

/**
 * 保养计划并发门面：
 * 创建按"设备+保养项目"互斥（杜绝同项目并发建出生效计划）；
 * 修改/取消按计划id互斥（调整与取消并发也只一生效）。
 */
@Component
public class MaintPlanFacade {

    private final MaintConcurrencyService concurrencyService;
    private final MaintPlanService planService;

    public MaintPlanFacade(MaintConcurrencyService concurrencyService, MaintPlanService planService) {
        this.concurrencyService = concurrencyService;
        this.planService = planService;
    }

    public MaintPlan create(MaintPlanCreateRequest request) {
        return concurrencyService.runExclusive(
                MaintConcurrencyService.OP_PLAN_CREATE,
                MaintConcurrencyService.TARGET_PLAN_TEMPLATE,
                request.getDeviceId() + ":" + request.getTaskTemplate(),
                request.getOperator() == null ? "system" : request.getOperator(),
                request.getRequestId(), "创建保养计划",
                () -> planService.create(request));
    }

    public MaintPlan adjust(Long planId, PlanAdjustRequest request) {
        return concurrencyService.runExclusive(
                MaintConcurrencyService.OP_PLAN_ADJUST,
                "PLAN", String.valueOf(planId),
                request.getOperator(), request.getRequestId(), "调整保养计划",
                () -> planService.adjust(planId, request));
    }

    public MaintPlan cancel(Long planId, String operator, String requestId) {
        return concurrencyService.runExclusive(
                MaintConcurrencyService.OP_PLAN_CANCEL,
                "PLAN", String.valueOf(planId),
                operator, requestId, "取消保养计划",
                () -> planService.cancel(planId, operator, requestId));
    }
}
