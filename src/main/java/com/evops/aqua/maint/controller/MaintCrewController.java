package com.evops.aqua.maint.controller;

import com.evops.aqua.maint.dto.CrewCreateRequest;
import com.evops.aqua.maint.entity.MaintCrew;
import com.evops.aqua.maint.service.MaintCrewService;
import com.evops.common.ApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 维修人员档案。
 */
@RestController
@RequestMapping("/api/aqua/maintenance")
public class MaintCrewController {

    private final MaintCrewService crewService;

    public MaintCrewController(MaintCrewService crewService) {
        this.crewService = crewService;
    }

    @PostMapping("/crews")
    public ApiResponse<MaintCrew> create(@Validated @RequestBody CrewCreateRequest request) {
        return ApiResponse.ok(crewService.create(request));
    }

    @GetMapping("/crews")
    public ApiResponse<List<MaintCrew>> list() {
        return ApiResponse.ok(crewService.list());
    }
}
