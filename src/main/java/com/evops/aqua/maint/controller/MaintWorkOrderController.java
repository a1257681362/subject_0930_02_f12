package com.evops.aqua.maint.controller;

import com.evops.aqua.maint.dto.OrderAcceptRequest;
import com.evops.aqua.maint.dto.OrderAssignRequest;
import com.evops.aqua.maint.dto.OrderCompleteRequest;
import com.evops.aqua.maint.dto.OrderGenerateRequest;
import com.evops.aqua.maint.dto.PartReplaceRequest;
import com.evops.aqua.maint.dto.SafetyConfirmRequest;
import com.evops.aqua.maint.entity.MaintChangeEvidence;
import com.evops.aqua.maint.entity.MaintConcurrencyReject;
import com.evops.aqua.maint.entity.MaintPartReplacement;
import com.evops.aqua.maint.entity.MaintSafetyCheck;
import com.evops.aqua.maint.entity.MaintWorkOrder;
import com.evops.aqua.maint.service.MaintConcurrencyService;
import com.evops.aqua.maint.service.MaintEvidenceService;
import com.evops.aqua.maint.service.MaintSchedulerService;
import com.evops.aqua.maint.service.MaintWorkOrderFacade;
import com.evops.aqua.maint.service.MaintWorkOrderService;
import com.evops.common.ApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 保养工单全生命周期：生成→分派（窗口+人员）→开工→换件（留旧件序列号）→完工→验收
 * →安全确认→恢复运行并关闭。未验收不能关闭。所有同一操作并发只一生效，余者拒绝并审计。
 */
@RestController
@RequestMapping("/api/aqua/maintenance")
public class MaintWorkOrderController {

    private final MaintWorkOrderFacade orderFacade;
    private final MaintWorkOrderService orderService;
    private final MaintSchedulerService schedulerService;
    private final MaintEvidenceService evidenceService;
    private final MaintConcurrencyService concurrencyService;

    public MaintWorkOrderController(MaintWorkOrderFacade orderFacade,
                                    MaintWorkOrderService orderService,
                                    MaintSchedulerService schedulerService,
                                    MaintEvidenceService evidenceService,
                                    MaintConcurrencyService concurrencyService) {
        this.orderFacade = orderFacade;
        this.orderService = orderService;
        this.schedulerService = schedulerService;
        this.evidenceService = evidenceService;
        this.concurrencyService = concurrencyService;
    }

    /** 人工触发计划生成一期工单。 */
    @PostMapping("/orders/generate")
    public ApiResponse<MaintWorkOrder> generate(@Validated @RequestBody OrderGenerateRequest request) {
        return ApiResponse.ok(orderFacade.generate(request));
    }

    /** 手动执行一次调度扫描（生成到期工单 + 逾期升级），便于查询与验收。 */
    @PostMapping("/scheduler/scan")
    public ApiResponse<Map<String, Object>> scan() {
        return ApiResponse.ok(schedulerService.runManually());
    }

    @GetMapping("/orders")
    public ApiResponse<List<MaintWorkOrder>> list(@RequestParam(required = false) Long deviceId,
                                                  @RequestParam(required = false) String status,
                                                  @RequestParam(required = false) Long planId) {
        return ApiResponse.ok(orderService.listOrders(deviceId, status, planId));
    }

    @GetMapping("/orders/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        return ApiResponse.ok(orderService.detail(id));
    }

    @PostMapping("/orders/{id}/assign")
    public ApiResponse<MaintWorkOrder> assign(@PathVariable Long id,
                                              @Validated @RequestBody OrderAssignRequest request) {
        return ApiResponse.ok(orderFacade.assign(id, request));
    }

    @PostMapping("/orders/{id}/start")
    public ApiResponse<MaintWorkOrder> start(@PathVariable Long id,
                                             @RequestParam String operator,
                                             @RequestParam(required = false) String requestId) {
        return ApiResponse.ok(orderFacade.start(id, operator, requestId));
    }

    @PostMapping("/orders/{id}/part-replacements")
    public ApiResponse<MaintPartReplacement> replacePart(@PathVariable Long id,
                                                         @Validated @RequestBody PartReplaceRequest request) {
        return ApiResponse.ok(orderFacade.replacePart(id, request));
    }

    @PostMapping("/orders/{id}/complete")
    public ApiResponse<MaintWorkOrder> complete(@PathVariable Long id,
                                                @Validated @RequestBody OrderCompleteRequest request) {
        return ApiResponse.ok(orderFacade.complete(id, request));
    }

    @PostMapping("/orders/{id}/accept")
    public ApiResponse<MaintWorkOrder> accept(@PathVariable Long id,
                                              @Validated @RequestBody OrderAcceptRequest request) {
        return ApiResponse.ok(orderFacade.accept(id, request));
    }

    @PostMapping("/orders/{id}/safety-confirm")
    public ApiResponse<MaintSafetyCheck> safetyConfirm(@PathVariable Long id,
                                                       @Validated @RequestBody SafetyConfirmRequest request) {
        return ApiResponse.ok(orderFacade.confirmSafety(id, request));
    }

    @PostMapping("/orders/{id}/close")
    public ApiResponse<MaintWorkOrder> close(@PathVariable Long id,
                                             @RequestParam String operator,
                                             @RequestParam(required = false) String requestId) {
        return ApiResponse.ok(orderFacade.close(id, operator, requestId));
    }

    /** 备件谱系：按旧/新序列号追溯换件记录（旧件序列号永久保留）。 */
    @GetMapping("/replacements")
    public ApiResponse<List<MaintPartReplacement>> replacements(@RequestParam String serialNo) {
        return ApiResponse.ok(orderService.replacementsOf(serialNo));
    }

    /** 变更证据查询：按对象/动作/请求号追溯。 */
    @GetMapping("/evidence")
    public ApiResponse<List<MaintChangeEvidence>> evidence(@RequestParam(required = false) String entityType,
                                                           @RequestParam(required = false) Long entityId,
                                                           @RequestParam(required = false) String requestId,
                                                           @RequestParam(required = false) String action) {
        return ApiResponse.ok(evidenceService.list(entityType, entityId, requestId, action));
    }

    /** 并发拒单审计：明确谁生效、谁被拒。 */
    @GetMapping("/concurrency-rejects")
    public ApiResponse<List<MaintConcurrencyReject>> rejects(@RequestParam(required = false) String targetType,
                                                             @RequestParam(required = false) String targetId) {
        if (targetType == null && targetId == null) {
            return ApiResponse.ok(concurrencyService.listAllRejects());
        }
        return ApiResponse.ok(concurrencyService.listRejects(targetType, targetId));
    }
}
