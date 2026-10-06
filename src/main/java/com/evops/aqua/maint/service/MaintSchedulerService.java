package com.evops.aqua.maint.service;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 保养调度入口（Quartz 调用）：先按计划生成到期工单，再扫描逾期工单升级。
 * 两个扫描自身幂等：未关闭工单不重复生成、已升级不重复升级，重启补跑安全。
 */
@Service
public class MaintSchedulerService {

    private final MaintWorkOrderService orderService;

    public MaintSchedulerService(MaintWorkOrderService orderService) {
        this.orderService = orderService;
    }

    public void runScheduledScan() {
        LocalDateTime now = LocalDateTime.now();
        orderService.runGenerateScan(now);
        orderService.runEscalationScan(now);
    }

    /** 供测试/手动触发查看结果。 */
    public Map<String, Object> runManually() {
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("generate", orderService.runGenerateScan(now));
        result.put("escalation", orderService.runEscalationScan(now));
        return result;
    }
}
