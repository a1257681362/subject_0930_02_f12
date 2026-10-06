package com.evops.aqua.maint.controller;

import com.evops.aqua.maint.dto.PlanAdjustRequest;
import com.evops.aqua.maint.dto.MaintPlanCreateRequest;
import com.evops.aqua.maint.entity.MaintPlan;
import com.evops.aqua.maint.service.MaintPlanFacade;
import com.evops.aqua.maint.service.MaintPlanService;
import com.evops.common.ApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 保养计划：HOURS/CALENDAR 双触发；调整只影响未生成工单；并发创建/调整/取消只一生效。
 */
@RestController
@RequestMapping("/api/aqua/maintenance")
public class MaintPlanController {

    private final MaintPlanFacade planFacade;
    private final MaintPlanService planService;

    public MaintPlanController(MaintPlanFacade planFacade, MaintPlanService planService) {
        this.planFacade = planFacade;
        this.planService = planService;
    }

    @PostMapping("/plans")
    public ApiResponse<MaintPlan> create(@Validated @RequestBody MaintPlanCreateRequest request) {
        return ApiResponse.ok(planFacade.create(request));
    }

    @PutMapping("/plans/{id}/adjust")
    public ApiResponse<MaintPlan> adjust(@PathVariable Long id,
                                         @Validated @RequestBody PlanAdjustRequest request) {
        return ApiResponse.ok(planFacade.adjust(id, request));
    }

    @PostMapping("/plans/{id}/cancel")
    public ApiResponse<MaintPlan> cancel(@PathVariable Long id,
                                         @RequestParam String operator,
                                         @RequestParam(required = false) String requestId) {
        return ApiResponse.ok(planFacade.cancel(id, operator, requestId));
    }

    @GetMapping("/plans")
    public ApiResponse<List<MaintPlan>> list(@RequestParam(required = false) Long deviceId) {
        return ApiResponse.ok(planService.list(deviceId));
    }

    @GetMapping("/plans/{id}")
    public ApiResponse<MaintPlan> detail(@PathVariable Long id) {
        return ApiResponse.ok(planService.getOrThrow(id));
    }
}
