package com.evops.aqua.maint.service;

import com.evops.aqua.maint.dto.WindowCreateRequest;
import com.evops.aqua.maint.entity.MaintShutdownWindow;
import org.springframework.stereotype.Component;

/**
 * 停机窗口并发门面：同设备并发开放窗口串行化，只一生效、余者拒绝并保留审计。
 */
@Component
public class MaintWindowFacade {

    private final MaintConcurrencyService concurrencyService;
    private final MaintWindowService windowService;

    public MaintWindowFacade(MaintConcurrencyService concurrencyService, MaintWindowService windowService) {
        this.concurrencyService = concurrencyService;
        this.windowService = windowService;
    }

    public MaintShutdownWindow open(WindowCreateRequest request) {
        return concurrencyService.runExclusive(
                MaintConcurrencyService.OP_WINDOW_OPEN,
                MaintConcurrencyService.TARGET_DEVICE,
                String.valueOf(request.getDeviceId()),
                request.getOperator(), request.getRequestId(), "开放停机窗口",
                () -> windowService.create(request));
    }
}
