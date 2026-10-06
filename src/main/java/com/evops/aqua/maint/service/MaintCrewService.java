package com.evops.aqua.maint.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.maint.dto.CrewCreateRequest;
import com.evops.aqua.maint.entity.MaintChangeEvidence;
import com.evops.aqua.maint.entity.MaintCrew;
import com.evops.aqua.maint.mapper.MaintCrewMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 维修人员档案：工单分派对象，必须为 ACTIVE。
 */
@Service
public class MaintCrewService {

    private final MaintCrewMapper crewMapper;
    private final MaintEvidenceService evidenceService;

    public MaintCrewService(MaintCrewMapper crewMapper, MaintEvidenceService evidenceService) {
        this.crewMapper = crewMapper;
        this.evidenceService = evidenceService;
    }

    @Transactional(rollbackFor = Exception.class)
    public MaintCrew create(CrewCreateRequest request) {
        if (request.getRequestId() != null && !request.getRequestId().trim().isEmpty()) {
            MaintCrew same = crewMapper.selectOne(new QueryWrapper<MaintCrew>()
                    .eq("request_id", request.getRequestId()).last("limit 1"));
            if (same != null) {
                return same;
            }
        }
        Long codeExists = crewMapper.selectCount(
                new QueryWrapper<MaintCrew>().eq("crew_code", request.getCrewCode()));
        if (codeExists != null && codeExists > 0) {
            throw new IllegalArgumentException("人员编码已存在：" + request.getCrewCode());
        }
        Long nameExists = crewMapper.selectCount(
                new QueryWrapper<MaintCrew>().eq("crew_name", request.getCrewName()));
        if (nameExists != null && nameExists > 0) {
            throw new IllegalArgumentException("人员姓名已存在：" + request.getCrewName());
        }
        MaintCrew crew = new MaintCrew();
        crew.setCrewCode(request.getCrewCode());
        crew.setCrewName(request.getCrewName());
        crew.setSkill(request.getSkill());
        crew.setStatus(MaintCrew.STATUS_ACTIVE);
        crew.setRequestId(request.getRequestId());
        crewMapper.insert(crew);
        evidenceService.record(MaintChangeEvidence.ENTITY_CREW, crew.getId(),
                MaintChangeEvidence.ACTION_CREATE, request.getRequestId(),
                request.getOperator() == null ? "system" : request.getOperator(),
                null, crew, "登记维修人员[" + crew.getCrewName() + "]，技能=" + crew.getSkill());
        return crew;
    }

    public MaintCrew requireActive(Long id) {
        MaintCrew crew = crewMapper.selectById(id);
        if (crew == null) {
            throw new IllegalArgumentException("维修人员不存在，id=" + id);
        }
        if (!MaintCrew.STATUS_ACTIVE.equals(crew.getStatus())) {
            throw new IllegalArgumentException("维修人员已停用：" + crew.getCrewName());
        }
        return crew;
    }

    public List<MaintCrew> list() {
        return crewMapper.selectList(new QueryWrapper<MaintCrew>().orderByAsc("id"));
    }
}
