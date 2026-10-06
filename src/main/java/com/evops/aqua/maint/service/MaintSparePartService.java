package com.evops.aqua.maint.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.maint.dto.SparePartInRequest;
import com.evops.aqua.maint.entity.MaintChangeEvidence;
import com.evops.aqua.maint.entity.MaintSparePart;
import com.evops.aqua.maint.mapper.MaintSparePartMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 备件台账：序列号逐件管理；换件由工单服务驱动状态流转，旧件序列号不删除不改写。
 */
@Service
public class MaintSparePartService {

    private final MaintSparePartMapper partMapper;
    private final MaintEvidenceService evidenceService;

    public MaintSparePartService(MaintSparePartMapper partMapper, MaintEvidenceService evidenceService) {
        this.partMapper = partMapper;
        this.evidenceService = evidenceService;
    }

    @Transactional(rollbackFor = Exception.class)
    public MaintSparePart stockIn(SparePartInRequest request) {
        if (request.getRequestId() != null && !request.getRequestId().trim().isEmpty()) {
            MaintSparePart same = partMapper.selectOne(new QueryWrapper<MaintSparePart>()
                    .eq("request_id", request.getRequestId()).last("limit 1"));
            if (same != null) {
                return same;
            }
        }
        Long exists = partMapper.selectCount(
                new QueryWrapper<MaintSparePart>().eq("serial_no", request.getSerialNo()));
        if (exists != null && exists > 0) {
            throw new IllegalArgumentException("备件序列号已存在：" + request.getSerialNo());
        }
        MaintSparePart part = new MaintSparePart();
        part.setPartName(request.getPartName());
        part.setSerialNo(request.getSerialNo());
        part.setSpec(request.getSpec());
        part.setStatus(MaintSparePart.STATUS_IN_STOCK);
        part.setRequestId(request.getRequestId());
        partMapper.insert(part);
        evidenceService.record(MaintChangeEvidence.ENTITY_PART, part.getId(),
                MaintChangeEvidence.ACTION_CREATE, request.getRequestId(), request.getOperator(),
                null, part, "备件入库：" + part.getPartName() + "，序列号=" + part.getSerialNo());
        return part;
    }

    /** 按序列号查询，不存在抛异常。 */
    public MaintSparePart getBySerialOrThrow(String serialNo) {
        MaintSparePart part = partMapper.selectOne(new QueryWrapper<MaintSparePart>()
                .eq("serial_no", serialNo).last("limit 1"));
        if (part == null) {
            throw new IllegalArgumentException("备件序列号不存在：" + serialNo);
        }
        return part;
    }

    public List<MaintSparePart> list(String status, String partName) {
        QueryWrapper<MaintSparePart> wrapper = new QueryWrapper<>();
        wrapper.eq(status != null && !status.trim().isEmpty(), "status", status);
        wrapper.eq(partName != null && !partName.trim().isEmpty(), "part_name", partName);
        wrapper.orderByAsc("id");
        return partMapper.selectList(wrapper);
    }
}
