package com.evops.aqua.maint.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.maint.dto.PlanAdjustRequest;
import com.evops.aqua.maint.dto.MaintPlanCreateRequest;
import com.evops.aqua.maint.entity.MaintChangeEvidence;
import com.evops.aqua.maint.entity.MaintDevice;
import com.evops.aqua.maint.entity.MaintPlan;
import com.evops.aqua.maint.entity.MaintPlanPart;
import com.evops.aqua.maint.mapper.MaintPlanMapper;
import com.evops.aqua.maint.mapper.MaintPlanPartMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 保养计划：HOURS 按运行小时 / CALENDAR 按日历周期；维护"下次到期"排期。
 * 调整间隔/提前量只重算未生成工单的后续排期（next_due_*），已生成工单不受影响。
 * 同设备同保养项目至多一个生效计划（active_flag TRUE 唯一，取消置 NULL 保留历史）。
 */
@Service
public class MaintPlanService {

    private final MaintPlanMapper planMapper;
    private final MaintPlanPartMapper planPartMapper;
    private final MaintDeviceService deviceService;
    private final MaintEvidenceService evidenceService;

    public MaintPlanService(MaintPlanMapper planMapper,
                            MaintPlanPartMapper planPartMapper,
                            MaintDeviceService deviceService,
                            MaintEvidenceService evidenceService) {
        this.planMapper = planMapper;
        this.planPartMapper = planPartMapper;
        this.deviceService = deviceService;
        this.evidenceService = evidenceService;
    }

    @Transactional(rollbackFor = Exception.class)
    public MaintPlan create(MaintPlanCreateRequest request) {
        MaintDevice device = deviceService.getOrThrow(request.getDeviceId());
        validateTriggerParams(request.getTriggerType(), request.getIntervalHours(), request.getIntervalDays());
        if (request.getEndDate() != null && request.getEndDate().isBefore(request.getStartDate())) {
            throw new IllegalArgumentException("计划结束日期不能早于开始日期");
        }
        Long noExists = planMapper.selectCount(new QueryWrapper<MaintPlan>().eq("plan_no", request.getPlanNo()));
        if (noExists != null && noExists > 0) {
            throw new IllegalArgumentException("保养计划编号已存在：" + request.getPlanNo());
        }
        Long activeExists = planMapper.selectCount(new QueryWrapper<MaintPlan>()
                .eq("device_id", device.getId())
                .eq("task_template", request.getTaskTemplate())
                .eq("active_flag", Boolean.TRUE));
        if (activeExists != null && activeExists > 0) {
            throw new IllegalArgumentException("设备[" + device.getDeviceCode() + "]的保养项目["
                    + request.getTaskTemplate() + "]已存在生效计划，不能重复创建");
        }

        MaintPlan plan = new MaintPlan();
        plan.setPlanNo(request.getPlanNo());
        plan.setDeviceId(device.getId());
        plan.setTaskTemplate(request.getTaskTemplate());
        plan.setTriggerType(request.getTriggerType());
        plan.setIntervalHours(request.getIntervalHours());
        plan.setIntervalDays(request.getIntervalDays());
        plan.setLeadHours(request.getLeadHours() == null ? BigDecimal.ZERO : request.getLeadHours());
        plan.setLeadDays(request.getLeadDays() == null ? 0 : request.getLeadDays());
        plan.setStartDate(request.getStartDate());
        plan.setEndDate(request.getEndDate());
        plan.setStatus(MaintPlan.STATUS_ACTIVE);
        plan.setActiveFlag(Boolean.TRUE);
        plan.setDueCounter(0);
        // 锚点取设备上次保养基准；首到期按间隔减提前量计算
        if (MaintPlan.TRIGGER_HOURS.equals(request.getTriggerType())) {
            plan.setAnchorMeter(device.getLastMaintainMeter());
            plan.setAnchorDate(request.getStartDate());
            plan.setNextDueMeter(firstDueMeter(plan));
            plan.setNextDueDate(null);
        } else {
            LocalDate anchor = device.getLastMaintainDate() != null
                    && !device.getLastMaintainDate().isBefore(request.getStartDate())
                    ? device.getLastMaintainDate() : request.getStartDate();
            plan.setAnchorMeter(device.getLastMaintainMeter());
            plan.setAnchorDate(anchor);
            plan.setNextDueMeter(null);
            plan.setNextDueDate(firstDueDate(plan));
        }
        plan.setRequestId(request.getRequestId());
        plan.setRemark(request.getRemark());
        planMapper.insert(plan);

        List<String> partNames = new ArrayList<>();
        if (request.getPartNames() != null) {
            for (String name : request.getPartNames()) {
                if (name == null || name.trim().isEmpty() || partNames.contains(name.trim())) {
                    continue;
                }
                partNames.add(name.trim());
                MaintPlanPart part = new MaintPlanPart();
                part.setPlanId(plan.getId());
                part.setPartName(name.trim());
                planPartMapper.insert(part);
            }
        }
        evidenceService.record(MaintChangeEvidence.ENTITY_PLAN, plan.getId(),
                MaintChangeEvidence.ACTION_CREATE, request.getRequestId(),
                request.getOperator() == null ? "system" : request.getOperator(),
                null, plan, "创建保养计划[" + plan.getPlanNo() + "]：设备[" + device.getDeviceCode()
                        + "]项目[" + plan.getTaskTemplate() + "]触发方式=" + plan.getTriggerType()
                        + (MaintPlan.TRIGGER_HOURS.equals(plan.getTriggerType())
                        ? "，间隔" + plan.getIntervalHours() + "h，下次到期读数=" + plan.getNextDueMeter()
                        : "，间隔" + plan.getIntervalDays() + "天，下次到期日=" + plan.getNextDueDate()));
        return plan;
    }

    /**
     * 调整计划：间隔/提前量变更后，从当前锚点与已生成期数重算"下次到期"。
     * 已生成工单持有自己的快照，一律不变——调整只影响尚未生成的后续工单。
     */
    @Transactional(rollbackFor = Exception.class)
    public MaintPlan adjust(Long planId, PlanAdjustRequest request) {
        MaintPlan before = copyOf(getOrThrow(planId));
        MaintPlan plan = getOrThrow(planId);
        requireActive(plan);
        if (MaintPlan.TRIGGER_HOURS.equals(plan.getTriggerType())) {
            if (request.getIntervalHours() != null) {
                requirePositiveHours(request.getIntervalHours());
                plan.setIntervalHours(request.getIntervalHours());
            }
            if (request.getLeadHours() != null) {
                requireNonNegative(request.getLeadHours());
                plan.setLeadHours(request.getLeadHours());
            }
            plan.setNextDueMeter(dueMeterAfter(plan, plan.getDueCounter() + 1));
        } else {
            if (request.getIntervalDays() != null) {
                if (request.getIntervalDays() <= 0) {
                    throw new IllegalArgumentException("日历周期间隔天数必须大于0");
                }
                plan.setIntervalDays(request.getIntervalDays());
            }
            if (request.getLeadDays() != null) {
                if (request.getLeadDays() < 0) {
                    throw new IllegalArgumentException("提前天数不能为负");
                }
                plan.setLeadDays(request.getLeadDays());
            }
            plan.setNextDueDate(dueDateAfter(plan, plan.getDueCounter() + 1));
        }
        if (request.getRemark() != null) {
            plan.setRemark(request.getRemark());
        }
        planMapper.updateById(plan);
        evidenceService.record(MaintChangeEvidence.ENTITY_PLAN, plan.getId(),
                MaintChangeEvidence.ACTION_UPDATE, request.getRequestId(), request.getOperator(),
                before, plan, "调整保养计划[" + plan.getPlanNo()
                        + "]，仅影响未生成工单的后续排期；已生成工单不变");
        return plan;
    }

    @Transactional(rollbackFor = Exception.class)
    public MaintPlan cancel(Long planId, String operator, String requestId) {
        MaintPlan before = copyOf(getOrThrow(planId));
        MaintPlan plan = getOrThrow(planId);
        requireActive(plan);
        plan.setStatus(MaintPlan.STATUS_CANCELLED);
        plan.setActiveFlag(null); // 释放生效唯一槽位，历史行保留
        planMapper.updateById(plan);
        evidenceService.record(MaintChangeEvidence.ENTITY_PLAN, plan.getId(),
                MaintChangeEvidence.ACTION_CANCEL, requestId, operator, before, plan,
                "取消保养计划[" + plan.getPlanNo() + "]，已生成工单不受影响");
        return plan;
    }

    // ------------------------------------------------------------
    // 排期推进（供工单生成/关闭调用）
    // ------------------------------------------------------------

    /** 生成一期后推进到下一到期；不依赖设备当前读数，纯排期计算。 */
    @Transactional(rollbackFor = Exception.class)
    public void advanceAfterGenerated(MaintPlan plan, String generatedKey) {
        MaintPlan before = copyOf(plan);
        int next = plan.getDueCounter() + 1;
        plan.setDueCounter(next);
        plan.setLastGeneratedKey(generatedKey);
        if (MaintPlan.TRIGGER_HOURS.equals(plan.getTriggerType())) {
            plan.setNextDueMeter(dueMeterAfter(plan, next + 1));
        } else {
            plan.setNextDueDate(dueDateAfter(plan, next + 1));
        }
        planMapper.updateById(plan);
        evidenceService.record(MaintChangeEvidence.ENTITY_PLAN, plan.getId(),
                MaintChangeEvidence.ACTION_UPDATE, null, "system-scheduler", before, plan,
                "生成第" + next + "期工单后推进下次到期排期");
    }

    /**
     * 工单关闭后以实际完工基准重锚：HOURS 以完工读数、CALENDAR 以完工日重置，期数归零。
     */
    @Transactional(rollbackFor = Exception.class)
    public void reanchorAfterClose(MaintPlan plan, BigDecimal completedMeter, LocalDate completedDate) {
        MaintPlan before = copyOf(plan);
        plan.setDueCounter(0);
        plan.setLastGeneratedKey(null);
        if (MaintPlan.TRIGGER_HOURS.equals(plan.getTriggerType())) {
            plan.setAnchorMeter(completedMeter);
            plan.setAnchorDate(completedDate);
            plan.setNextDueMeter(dueMeterAfter(plan, 1));
        } else {
            plan.setAnchorMeter(completedMeter);
            plan.setAnchorDate(completedDate);
            plan.setNextDueDate(dueDateAfter(plan, 1));
        }
        planMapper.updateById(plan);
        evidenceService.record(MaintChangeEvidence.ENTITY_PLAN, plan.getId(),
                MaintChangeEvidence.ACTION_UPDATE, null, "system", before, plan,
                "工单关闭后按实际完工重锚后续排期");
    }

    public MaintPlan getOrThrow(Long id) {
        MaintPlan plan = planMapper.selectById(id);
        if (plan == null) {
            throw new IllegalArgumentException("保养计划不存在，id=" + id);
        }
        return plan;
    }

    public List<MaintPlan> listActive() {
        return planMapper.selectList(new QueryWrapper<MaintPlan>()
                .eq("active_flag", Boolean.TRUE).orderByAsc("id"));
    }

    public List<MaintPlan> list(Long deviceId) {
        QueryWrapper<MaintPlan> wrapper = new QueryWrapper<>();
        wrapper.eq(deviceId != null, "device_id", deviceId);
        wrapper.orderByAsc("id");
        return planMapper.selectList(wrapper);
    }

    public List<String> listPartNames(Long planId) {
        List<MaintPlanPart> parts = planPartMapper.selectList(
                new QueryWrapper<MaintPlanPart>().eq("plan_id", planId).orderByAsc("id"));
        List<String> names = new ArrayList<>();
        for (MaintPlanPart part : parts) {
            names.add(part.getPartName());
        }
        return names;
    }

    static void validateTriggerParams(String triggerType, BigDecimal intervalHours, Integer intervalDays) {
        if (MaintPlan.TRIGGER_HOURS.equals(triggerType)) {
            requirePositiveHours(intervalHours);
        } else if (MaintPlan.TRIGGER_CALENDAR.equals(triggerType)) {
            if (intervalDays == null || intervalDays <= 0) {
                throw new IllegalArgumentException("日历周期触发必须填写大于0的间隔天数");
            }
        } else {
            throw new IllegalArgumentException("触发类型非法（HOURS/CALENDAR）：" + triggerType);
        }
    }

    private static void requirePositiveHours(BigDecimal v) {
        if (v == null || v.signum() <= 0) {
            throw new IllegalArgumentException("运行小时触发必须填写大于0的间隔小时数");
        }
    }

    private static void requireNonNegative(BigDecimal v) {
        if (v == null || v.signum() < 0) {
            throw new IllegalArgumentException("提前小时数不能为负");
        }
    }

    private void requireActive(MaintPlan plan) {
        if (!MaintPlan.STATUS_ACTIVE.equals(plan.getStatus()) || !Boolean.TRUE.equals(plan.getActiveFlag())) {
            throw new IllegalArgumentException("计划非生效中，不能修改/取消：" + plan.getPlanNo());
        }
    }

    private BigDecimal firstDueMeter(MaintPlan plan) {
        return dueMeterAfter(plan, 1);
    }

    private LocalDate firstDueDate(MaintPlan plan) {
        return dueDateAfter(plan, 1);
    }

    /** 第 occurrence 期到期读数 = 锚点 + occurrence×间隔 − 提前量，不低于0。 */
    private BigDecimal dueMeterAfter(MaintPlan plan, int occurrence) {
        BigDecimal due = plan.getAnchorMeter()
                .add(plan.getIntervalHours().multiply(BigDecimal.valueOf(occurrence)))
                .subtract(plan.getLeadHours() == null ? BigDecimal.ZERO : plan.getLeadHours());
        return due.signum() < 0 ? BigDecimal.ZERO : due;
    }

    /** 第 occurrence 期到期日 = 锚点 + occurrence×间隔 − 提前量。 */
    private LocalDate dueDateAfter(MaintPlan plan, int occurrence) {
        int lead = plan.getLeadDays() == null ? 0 : plan.getLeadDays();
        return plan.getAnchorDate().plusDays((long) occurrence * plan.getIntervalDays() - lead);
    }

    private MaintPlan copyOf(MaintPlan source) {
        MaintPlan copy = new MaintPlan();
        org.springframework.beans.BeanUtils.copyProperties(source, copy);
        return copy;
    }
}
