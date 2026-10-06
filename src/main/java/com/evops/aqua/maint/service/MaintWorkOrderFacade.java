package com.evops.aqua.maint.service;

import com.evops.aqua.maint.dto.OrderAcceptRequest;
import com.evops.aqua.maint.dto.OrderAssignRequest;
import com.evops.aqua.maint.dto.OrderCompleteRequest;
import com.evops.aqua.maint.dto.OrderGenerateRequest;
import com.evops.aqua.maint.dto.PartReplaceRequest;
import com.evops.aqua.maint.dto.SafetyConfirmRequest;
import com.evops.aqua.maint.entity.MaintPartReplacement;
import com.evops.aqua.maint.entity.MaintSafetyCheck;
import com.evops.aqua.maint.entity.MaintWorkOrder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 工单并发门面：
 * 生成按"设备+保养项目"互斥（杜绝并发生成两张未关闭工单）；
 * 同一工单的全生命周期操作共用一把互斥（分派/开工/完工/验收/安全确认/关闭并发只一生效）。
 * 被拒方由仲裁器写拒单审计并抛冲突异常，接口层明确返回"谁生效、谁被拒"。
 */
@Component
public class MaintWorkOrderFacade {

    private final MaintConcurrencyService concurrencyService;
    private final MaintWorkOrderService orderService;

    public MaintWorkOrderFacade(MaintConcurrencyService concurrencyService,
                                MaintWorkOrderService orderService) {
        this.concurrencyService = concurrencyService;
        this.orderService = orderService;
    }

    public MaintWorkOrder generate(OrderGenerateRequest request) {
        return concurrencyService.runExclusive(
                MaintConcurrencyService.OP_ORDER_GENERATE,
                MaintConcurrencyService.TARGET_PLAN_TEMPLATE,
                "PLAN:" + request.getPlanId(),
                request.getOperator(), request.getRequestId(), "生成保养工单",
                () -> orderService.generateManually(
                        request.getPlanId(), request.getOperator(), request.getRequestId(), LocalDateTime.now()));
    }

    public MaintWorkOrder assign(Long orderId, OrderAssignRequest request) {
        return lifecycle(orderId, "分派工单", request.getOperator(), request.getRequestId(),
                () -> orderService.assign(orderId, request));
    }

    public MaintWorkOrder start(Long orderId, String operator, String requestId) {
        return lifecycle(orderId, "工单开工", operator, requestId,
                () -> orderService.start(orderId, operator, requestId, LocalDateTime.now()));
    }

    public MaintPartReplacement replacePart(Long orderId, PartReplaceRequest request) {
        return lifecycle(orderId, "工单换件", request.getReplacedBy(), request.getRequestId(),
                () -> orderService.replacePart(orderId, request));
    }

    public MaintWorkOrder complete(Long orderId, OrderCompleteRequest request) {
        return lifecycle(orderId, "工单完工", request.getOperator(), request.getRequestId(),
                () -> orderService.complete(orderId, request));
    }

    public MaintWorkOrder accept(Long orderId, OrderAcceptRequest request) {
        return lifecycle(orderId, "工单验收", request.getAcceptedBy(), request.getRequestId(),
                () -> orderService.accept(orderId, request));
    }

    public MaintSafetyCheck confirmSafety(Long orderId, SafetyConfirmRequest request) {
        return lifecycle(orderId, "恢复运行安全确认", request.getConfirmedBy(), request.getRequestId(),
                () -> orderService.confirmSafety(orderId, request));
    }

    public MaintWorkOrder close(Long orderId, String operator, String requestId) {
        return lifecycle(orderId, "关闭工单并恢复运行", operator, requestId,
                () -> orderService.close(orderId, operator, requestId, LocalDateTime.now()));
    }

    private <T> T lifecycle(Long orderId, String desc, String operator, String requestId,
                            java.util.function.Supplier<T> action) {
        return concurrencyService.runExclusive(
                MaintConcurrencyService.OP_ORDER_LIFECYCLE,
                MaintConcurrencyService.TARGET_ORDER, String.valueOf(orderId),
                operator, requestId, desc, action);
    }
}
