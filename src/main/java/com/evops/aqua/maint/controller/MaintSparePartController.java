package com.evops.aqua.maint.controller;

import com.evops.aqua.maint.dto.SparePartInRequest;
import com.evops.aqua.maint.entity.MaintSparePart;
import com.evops.aqua.maint.service.MaintSparePartService;
import com.evops.common.ApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 备件序列号台账。
 */
@RestController
@RequestMapping("/api/aqua/maintenance")
public class MaintSparePartController {

    private final MaintSparePartService partService;

    public MaintSparePartController(MaintSparePartService partService) {
        this.partService = partService;
    }

    @PostMapping("/spare-parts")
    public ApiResponse<MaintSparePart> stockIn(@Validated @RequestBody SparePartInRequest request) {
        return ApiResponse.ok(partService.stockIn(request));
    }

    @GetMapping("/spare-parts")
    public ApiResponse<List<MaintSparePart>> list(@RequestParam(required = false) String status,
                                                  @RequestParam(required = false) String partName) {
        return ApiResponse.ok(partService.list(status, partName));
    }
}
