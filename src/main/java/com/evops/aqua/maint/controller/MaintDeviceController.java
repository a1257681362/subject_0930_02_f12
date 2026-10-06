package com.evops.aqua.maint.controller;

import com.evops.aqua.maint.dto.MaintDeviceRegisterRequest;
import com.evops.aqua.maint.dto.MeterReadingRequest;
import com.evops.aqua.maint.entity.MaintDevice;
import com.evops.aqua.maint.entity.MaintMeterReading;
import com.evops.aqua.maint.service.MaintDeviceService;
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
 * 设备档案与运行小时上报。
 */
@RestController
@RequestMapping("/api/aqua/maintenance")
public class MaintDeviceController {

    private final MaintDeviceService deviceService;

    public MaintDeviceController(MaintDeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @PostMapping("/devices")
    public ApiResponse<MaintDevice> register(@Validated @RequestBody MaintDeviceRegisterRequest request) {
        return ApiResponse.ok(deviceService.register(request));
    }

    @GetMapping("/devices")
    public ApiResponse<List<MaintDevice>> list(@RequestParam(required = false) String deviceType,
                                               @RequestParam(required = false) String status) {
        return ApiResponse.ok(deviceService.list(deviceType, status));
    }

    @GetMapping("/devices/{id}")
    public ApiResponse<MaintDevice> detail(@PathVariable Long id) {
        return ApiResponse.ok(deviceService.getOrThrow(id));
    }

    @PostMapping("/devices/{id}/meter-readings")
    public ApiResponse<MaintMeterReading> reportReading(@PathVariable Long id,
                                                        @Validated @RequestBody MeterReadingRequest request) {
        return ApiResponse.ok(deviceService.reportReading(id, request));
    }
}
