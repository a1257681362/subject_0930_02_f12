package com.evops.aqua.maint.controller;

import com.evops.aqua.maint.dto.WindowCreateRequest;
import com.evops.aqua.maint.entity.MaintShutdownWindow;
import com.evops.aqua.maint.service.MaintWindowFacade;
import com.evops.aqua.maint.service.MaintWindowService;
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

/**
 * 停机窗口：并发开放只一生效，余者拒绝并保留审计。
 */
@RestController
@RequestMapping("/api/aqua/maintenance")
public class MaintWindowController {

    private final MaintWindowFacade windowFacade;
    private final MaintWindowService windowService;

    public MaintWindowController(MaintWindowFacade windowFacade, MaintWindowService windowService) {
        this.windowFacade = windowFacade;
        this.windowService = windowService;
    }

    @PostMapping("/shutdown-windows")
    public ApiResponse<MaintShutdownWindow> open(@Validated @RequestBody WindowCreateRequest request) {
        return ApiResponse.ok(windowFacade.open(request));
    }

    @PostMapping("/shutdown-windows/{id}/close")
    public ApiResponse<MaintShutdownWindow> close(@PathVariable Long id,
                                                  @RequestParam String operator,
                                                  @RequestParam(required = false) String requestId) {
        return ApiResponse.ok(windowService.close(id, operator, requestId));
    }

    @GetMapping("/shutdown-windows")
    public ApiResponse<List<MaintShutdownWindow>> list(@RequestParam(required = false) Long deviceId,
                                                       @RequestParam(required = false) String status) {
        return ApiResponse.ok(windowService.list(deviceId, status));
    }
}
