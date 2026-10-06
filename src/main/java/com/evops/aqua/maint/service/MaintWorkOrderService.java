package com.evops.aqua.maint.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.maint.dto.OrderAcceptRequest;
import com.evops.aqua.maint.dto.OrderAssignRequest;
import com.evops.aqua.maint.dto.OrderCompleteRequest;
import com.evops.aqua.maint.dto.PartReplaceRequest;
import com.evops.aqua.maint.dto.SafetyConfirmRequest;
import com.evops.aqua.maint.entity.MaintChangeEvidence;
import com.evops.aqua.maint.entity.MaintCrew;
import com.evops.aqua.maint.entity.MaintDevice;
import com.evops.aqua.maint.entity.MaintOrderPartLine;
import com.evops.aqua.maint.entity.MaintPartReplacement;
import com.evops.aqua.maint.entity.MaintPlan;
import com.evops.aqua.maint.entity.MaintSafetyCheck;
import com.evops.aqua.maint.entity.MaintShutdownWindow;
import com.evops.aqua.maint.entity.MaintSparePart;
import com.evops.aqua.maint.entity.MaintWorkOrder;
import com.evops.aqua.maint.mapper.MaintOrderPartLineMapper;
import com.evops.aqua.maint.mapper.MaintPartReplacementMapper;
import com.evops.aqua.maint.mapper.MaintSafetyCheckMapper;
import com.evops.aqua.maint.mapper.MaintSparePartMapper;
import com.evops.aqua.maint.mapper.MaintWorkOrderMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 保养工单全生命周期：
 * 计划扫描/人工生成（HOURS 到读数、CALENDAR 到日期）→ 分派停机窗口+维修人员
 * → 窗口内开工（设备转MAINT）→ 换件（保留旧件序列号）→ 完工（备件需求须全换）
 * → 验收通过 → 安全确认三项全通过 → 恢复运行并关闭；未验收不能关闭。
 * 逾期未开工自动升级：到期+宽限一级（HIGH/现场主管），再+24h二级（URGENT/设备主管），已升级不重复。
 */
@Service
public class MaintWorkOrderService {

    /** 到期后宽限：超过 dueTime 此宽限仍未开工，升级一级。 */
    static final long LEVEL1_GRACE_HOURS = 2L;
    /** 一级升级后再过此小时数仍未开工，升级二级。 */
    static final long LEVEL2_AFTER_LEVEL1_HOURS = 24L;

    private final MaintWorkOrderMapper orderMapper;
    private final MaintOrderPartLineMapper lineMapper;
    private final MaintPartReplacementMapper replacementMapper;
    private final MaintSafetyCheckMapper safetyMapper;
    private final MaintSparePartMapper partMapper;
    private final MaintPlanService planService;
    private final MaintDeviceService deviceService;
    private final MaintWindowService windowService;
    private final MaintCrewService crewService;
    private final MaintEvidenceService evidenceService;

    public MaintWorkOrderService(MaintWorkOrderMapper orderMapper,
                                 MaintOrderPartLineMapper lineMapper,
                                 MaintPartReplacementMapper replacementMapper,
                                 MaintSafetyCheckMapper safetyMapper,
                                 MaintSparePartMapper partMapper,
                                 MaintPlanService planService,
                                 MaintDeviceService deviceService,
                                 MaintWindowService windowService,
                                 MaintCrewService crewService,
                                 MaintEvidenceService evidenceService) {
        this.orderMapper = orderMapper;
        this.lineMapper = lineMapper;
        this.replacementMapper = replacementMapper;
        this.safetyMapper = safetyMapper;
        this.partMapper = partMapper;
        this.planService = planService;
        this.deviceService = deviceService;
        this.windowService = windowService;
        this.crewService = crewService;
        this.evidenceService = evidenceService;
    }

    // ------------------------------------------------------------
    // 计划生成
    // ------------------------------------------------------------

    /**
     * 定时扫描：所有生效计划，HOURS 到读数阈值、CALENDAR 到到期日即生成工单。
     * 幂等：同设备同模板存在未关闭工单则跳过；期数键 lastGeneratedKey 防止重跑多写。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> runGenerateScan(LocalDateTime now) {
        List<MaintPlan> plans = planService.listActive();
        List<MaintWorkOrder> created = new ArrayList<>();
        int skippedOpen = 0;
        int skippedNotDue = 0;
        for (MaintPlan plan : plans) {
            if (planExpired(plan, now.toLocalDate())) {
                continue;
            }
            boolean due = MaintPlan.TRIGGER_HOURS.equals(plan.getTriggerType())
                    ? deviceService.getOrThrow(plan.getDeviceId()).getMeterHours()
                            .compareTo(plan.getNextDueMeter()) >= 0
                    : !now.toLocalDate().isBefore(plan.getNextDueDate());
            if (!due) {
                skippedNotDue++;
                continue;
            }
            if (findOpenOrder(plan.getDeviceId(), plan.getTaskTemplate()) != null) {
                skippedOpen++;
                continue;
            }
            int occurrence = plan.getDueCounter() + 1;
            String key = generationKey(plan, occurrence);
            if (key.equals(plan.getLastGeneratedKey())) {
                skippedOpen++;
                continue;
            }
            created.add(generateOne(plan, now, "system-scheduler", null));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scanTime", now);
        result.put("createdCount", created.size());
        result.put("skippedOpenCount", skippedOpen);
        result.put("skippedNotDueCount", skippedNotDue);
        result.put("created", created);
        return result;
    }

    /** 人工触发生成：立即生成一期到期工单（无论读数/日期是否到阈值）。 */
    @Transactional(rollbackFor = Exception.class)
    public MaintWorkOrder generateManually(Long planId, String operator, String requestId, LocalDateTime now) {
        MaintPlan plan = planService.getOrThrow(planId);
        if (!MaintPlan.STATUS_ACTIVE.equals(plan.getStatus()) || !Boolean.TRUE.equals(plan.getActiveFlag())) {
            throw new IllegalArgumentException("计划非生效中，不能生成工单：" + plan.getPlanNo());
        }
        if (findOpenOrder(plan.getDeviceId(), plan.getTaskTemplate()) != null) {
            throw new IllegalArgumentException("设备该保养项目已有未关闭工单，不能重复生成（先完成并关闭现有工单）");
        }
        int occurrence = plan.getDueCounter() + 1;
        String key = generationKey(plan, occurrence);
        if (key.equals(plan.getLastGeneratedKey())) {
            throw new IllegalArgumentException("该期工单已生成且计划尚未推进，请勿重复生成");
        }
        return generateOne(plan, now, operator, requestId);
    }

    private MaintWorkOrder generateOne(MaintPlan plan, LocalDateTime now, String operator, String requestId) {
        MaintDevice device = deviceService.getOrThrow(plan.getDeviceId());
        int occurrence = plan.getDueCounter() + 1;
        String key = generationKey(plan, occurrence);
        LocalDateTime dueTime = MaintPlan.TRIGGER_HOURS.equals(plan.getTriggerType())
                ? now
                : plan.getNextDueDate().atTime(LocalTime.MIN);

        // 工单号跨周期不复用：按该计划历史工单总数取递增序号（关闭重锚后也不与历史撞号）
        Long historyCount = orderMapper.selectCount(
                new QueryWrapper<MaintWorkOrder>().eq("plan_id", plan.getId()));
        int orderSeq = (historyCount == null ? 0 : historyCount.intValue()) + 1;

        MaintWorkOrder order = new MaintWorkOrder();
        order.setOrderNo("WO-" + plan.getId() + "-" + orderSeq);
        order.setPlanId(plan.getId());
        order.setDeviceId(device.getId());
        order.setTaskTemplate(plan.getTaskTemplate());
        order.setTriggerSource(MaintPlan.TRIGGER_HOURS.equals(plan.getTriggerType())
                ? MaintPlan.TRIGGER_HOURS : MaintPlan.TRIGGER_CALENDAR);
        order.setDueTime(dueTime);
        order.setPriority(MaintWorkOrder.PRIORITY_NORMAL);
        order.setStatus(MaintWorkOrder.STATUS_PLANNED);
        order.setOpenFlag(Boolean.TRUE);
        order.setAcceptedFlag(Boolean.FALSE);
        order.setEscalationLevel(0);
        order.setRequestId(requestId);
        orderMapper.insert(order);

        for (String partName : planService.listPartNames(plan.getId())) {
            MaintOrderPartLine line = new MaintOrderPartLine();
            line.setOrderId(order.getId());
            line.setPartName(partName);
            line.setReplacedFlag(Boolean.FALSE);
            lineMapper.insert(line);
        }

        planService.advanceAfterGenerated(plan, key);
        evidenceService.record(MaintChangeEvidence.ENTITY_ORDER, order.getId(),
                MaintChangeEvidence.ACTION_GENERATE, requestId, operator, null, order,
                "按计划[" + plan.getPlanNo() + "]生成第" + occurrence + "期工单" + order.getOrderNo()
                        + "，触发=" + order.getTriggerSource() + "，到期=" + dueTime
                        + "，关联备件需求=" + planService.listPartNames(plan.getId()));
        return order;
    }

    // ------------------------------------------------------------
    // 分派 / 开工
    // ------------------------------------------------------------

    @Transactional(rollbackFor = Exception.class)
    public MaintWorkOrder assign(Long orderId, OrderAssignRequest request) {
        MaintWorkOrder before = copyOf(getOrThrow(orderId));
        MaintWorkOrder order = getOrThrow(orderId);
        if (!MaintWorkOrder.STATUS_PLANNED.equals(order.getStatus())) {
            throw new IllegalArgumentException("仅待分派(PLANNED)工单可分派，当前状态：" + order.getStatus());
        }
        MaintShutdownWindow window = windowService.getOrThrow(request.getWindowId());
        if (!MaintShutdownWindow.STATUS_OPEN.equals(window.getStatus())) {
            throw new IllegalArgumentException("停机窗口[" + window.getWindowNo() + "]已关闭，不能分派");
        }
        if (!window.getDeviceId().equals(order.getDeviceId())) {
            throw new IllegalArgumentException("停机窗口所属设备与工单设备不一致");
        }
        MaintCrew crew = crewService.requireActive(request.getCrewId());

        order.setWindowId(window.getId());
        order.setAssignedCrewId(crew.getId());
        order.setAssignedCrewName(crew.getCrewName());
        order.setStatus(MaintWorkOrder.STATUS_ASSIGNED);
        orderMapper.updateById(order);
        evidenceService.record(MaintChangeEvidence.ENTITY_ORDER, order.getId(),
                MaintChangeEvidence.ACTION_ASSIGN, request.getRequestId(), request.getOperator(),
                before, order, "分派停机窗口[" + window.getWindowNo() + "]与维修人员["
                        + crew.getCrewName() + "]");
        return order;
    }

    @Transactional(rollbackFor = Exception.class)
    public MaintWorkOrder start(Long orderId, String operator, String requestId, LocalDateTime now) {
        MaintWorkOrder before = copyOf(getOrThrow(orderId));
        MaintWorkOrder order = getOrThrow(orderId);
        if (!MaintWorkOrder.STATUS_ASSIGNED.equals(order.getStatus())) {
            throw new IllegalArgumentException("仅已分派(ASSIGNED)工单可开工，当前状态：" + order.getStatus());
        }
        MaintShutdownWindow window = windowService.getOrThrow(order.getWindowId());
        if (now.isBefore(window.getStartTime()) || now.isAfter(window.getEndTime())) {
            throw new IllegalArgumentException("当前时间不在停机窗口[" + window.getWindowNo() + "]区间 "
                    + window.getStartTime() + "~" + window.getEndTime() + "，不能开工");
        }
        MaintDevice device = deviceService.getOrThrow(order.getDeviceId());
        order.setStatus(MaintWorkOrder.STATUS_IN_PROGRESS);
        order.setStartedTime(now);
        order.setMeterSnapshot(device.getMeterHours());
        orderMapper.updateById(order);

        MaintDevice beforeDevice = copyOfDevice(device);
        device.setStatus(MaintDevice.STATUS_MAINT);
        // 停机期间读数冻结属现场管理；设备状态置保养停机即可
        deviceServiceApplyStatus(device);
        evidenceService.record(MaintChangeEvidence.ENTITY_ORDER, order.getId(),
                MaintChangeEvidence.ACTION_START, requestId, operator, before, order,
                "工单开工，设备[" + device.getDeviceCode() + "]转入保养停机，开工读数="
                        + order.getMeterSnapshot());
        evidenceService.record(MaintChangeEvidence.ENTITY_DEVICE, device.getId(),
                MaintChangeEvidence.ACTION_UPDATE, requestId, operator, beforeDevice, device,
                "保养工单" + order.getOrderNo() + "开工，设备转入MAINT");
        return order;
    }

    // ------------------------------------------------------------
    // 换件：旧件序列号永久保留
    // ------------------------------------------------------------

    @Transactional(rollbackFor = Exception.class)
    public MaintPartReplacement replacePart(Long orderId, PartReplaceRequest request) {
        MaintPartReplacement same = replacementMapper.selectOne(new QueryWrapper<MaintPartReplacement>()
                .eq("request_id", request.getRequestId()).last("limit 1"));
        if (same != null) {
            return same;
        }
        MaintWorkOrder order = getOrThrow(orderId);
        if (!MaintWorkOrder.STATUS_IN_PROGRESS.equals(order.getStatus())
                && !MaintWorkOrder.STATUS_REJECTED.equals(order.getStatus())) {
            throw new IllegalArgumentException("仅施工中(IN_PROGRESS)或返修(REJECTED)工单可换件，当前状态："
                    + order.getStatus());
        }
        String disposition = request.getOldDisposition() == null || request.getOldDisposition().trim().isEmpty()
                ? MaintPartReplacement.DISPOSITION_RECYCLED : request.getOldDisposition().trim();
        if (!MaintPartReplacement.DISPOSITION_RECYCLED.equals(disposition)
                && !MaintPartReplacement.DISPOSITION_DISPOSED.equals(disposition)) {
            throw new IllegalArgumentException("旧件处置方式非法（RECYCLED/DISPOSED）：" + disposition);
        }

        // 输入前置校验：新件必须存在且在库
        MaintSparePart newPart = partMapper.selectOne(new QueryWrapper<MaintSparePart>()
                .eq("serial_no", request.getNewSerialNo()).last("limit 1"));
        if (newPart == null) {
            throw new IllegalArgumentException("新件序列号不在备件台账：" + request.getNewSerialNo());
        }
        if (!MaintSparePart.STATUS_IN_STOCK.equals(newPart.getStatus())) {
            throw new IllegalArgumentException("新件[" + request.getNewSerialNo() + "]非在库状态，当前="
                    + newPart.getStatus());
        }
        MaintOrderPartLine line = findLine(order.getId(), request.getPartName());
        if (Boolean.TRUE.equals(line.getReplacedFlag())) {
            throw new IllegalArgumentException("备件需求[" + request.getPartName() + "]已完成换件，不能重复更换");
        }
        // 旧件：台账中则必须为该设备已装机；不在台账也保留其序列号（现场拆机登记）
        MaintSparePart oldPart = partMapper.selectOne(new QueryWrapper<MaintSparePart>()
                .eq("serial_no", request.getOldSerialNo()).last("limit 1"));
        if (oldPart != null) {
            if (!MaintSparePart.STATUS_INSTALLED.equals(oldPart.getStatus())
                    || !order.getDeviceId().equals(oldPart.getInstalledDeviceId())) {
                throw new IllegalArgumentException("旧件[" + request.getOldSerialNo()
                        + "]不是本设备当前装机件（状态=" + oldPart.getStatus() + "）");
            }
            MaintSparePart beforeOld = copyOfPart(oldPart);
            oldPart.setStatus(disposition);
            oldPart.setInstalledDeviceId(null);
            oldPart.setInstalledTime(null);
            partMapper.updateById(oldPart);
            evidenceService.record(MaintChangeEvidence.ENTITY_PART, oldPart.getId(),
                    MaintChangeEvidence.ACTION_UPDATE, request.getRequestId(), request.getReplacedBy(),
                    beforeOld, oldPart, "旧件[" + oldPart.getSerialNo() + "]拆下，序列号保留，处置="
                            + disposition);
        }

        MaintSparePart beforeNew = copyOfPart(newPart);
        newPart.setStatus(MaintSparePart.STATUS_INSTALLED);
        newPart.setInstalledDeviceId(order.getDeviceId());
        newPart.setInstalledTime(LocalDateTime.now());
        partMapper.updateById(newPart);

        MaintPartReplacement replacement = new MaintPartReplacement();
        replacement.setOrderId(order.getId());
        replacement.setDeviceId(order.getDeviceId());
        replacement.setPartName(request.getPartName());
        replacement.setOldSerialNo(request.getOldSerialNo());
        replacement.setNewSerialNo(request.getNewSerialNo());
        replacement.setOldPartId(oldPart == null ? null : oldPart.getId());
        replacement.setNewPartId(newPart.getId());
        replacement.setOldDisposition(disposition);
        replacement.setReplacedBy(request.getReplacedBy());
        replacement.setReplacedTime(LocalDateTime.now());
        replacement.setRequestId(request.getRequestId());
        replacementMapper.insert(replacement);

        MaintOrderPartLine beforeLine = copyOfLine(line);
        line.setOldSerialNo(request.getOldSerialNo());
        line.setNewSerialNo(request.getNewSerialNo());
        line.setReplacedFlag(Boolean.TRUE);
        lineMapper.updateById(line);

        evidenceService.record(MaintChangeEvidence.ENTITY_PART, newPart.getId(),
                MaintChangeEvidence.ACTION_UPDATE, request.getRequestId(), request.getReplacedBy(),
                beforeNew, newPart, "新件[" + newPart.getSerialNo() + "]装机到设备id=" + order.getDeviceId());
        evidenceService.record(MaintChangeEvidence.ENTITY_REPLACEMENT, replacement.getId(),
                MaintChangeEvidence.ACTION_REPLACE_PART, request.getRequestId(), request.getReplacedBy(),
                null, replacement, "换件保留旧件序列号：旧[" + request.getOldSerialNo() + "]→新["
                        + request.getNewSerialNo() + "]，旧件处置=" + disposition);
        evidenceService.record(MaintChangeEvidence.ENTITY_PART_LINE, line.getId(),
                MaintChangeEvidence.ACTION_REPLACE_PART, request.getRequestId(), request.getReplacedBy(),
                beforeLine, line, "工单备件行登记新旧序列号");
        return replacement;
    }

    // ------------------------------------------------------------
    // 完工 / 验收
    // ------------------------------------------------------------

    @Transactional(rollbackFor = Exception.class)
    public MaintWorkOrder complete(Long orderId, OrderCompleteRequest request) {
        MaintWorkOrder before = copyOf(getOrThrow(orderId));
        MaintWorkOrder order = getOrThrow(orderId);
        if (!MaintWorkOrder.STATUS_IN_PROGRESS.equals(order.getStatus())
                && !MaintWorkOrder.STATUS_REJECTED.equals(order.getStatus())) {
            throw new IllegalArgumentException("仅施工中/返修工单可完工，当前状态：" + order.getStatus());
        }
        List<MaintOrderPartLine> lines = listLines(order.getId());
        List<String> unfinished = new ArrayList<>();
        for (MaintOrderPartLine line : lines) {
            if (!Boolean.TRUE.equals(line.getReplacedFlag())) {
                unfinished.add(line.getPartName());
            }
        }
        if (!unfinished.isEmpty()) {
            throw new IllegalArgumentException("以下备件需求尚未完成换件，不能完工：" + unfinished);
        }
        order.setStatus(MaintWorkOrder.STATUS_WORK_DONE);
        order.setCompletedTime(LocalDateTime.now());
        if (request.getRemark() != null) {
            order.setRemark(request.getRemark());
        }
        orderMapper.updateById(order);
        evidenceService.record(MaintChangeEvidence.ENTITY_ORDER, order.getId(),
                MaintChangeEvidence.ACTION_COMPLETE, request.getRequestId(), request.getOperator(),
                before, order, "工单完工，进入待验收");
        return order;
    }

    /**
     * 验收：通过置 ACCEPTED；驳回退回 REJECTED 返修且必须填写原因。
     * 未验收通过（accepted_flag=FALSE）的工单禁止关闭。
     */
    @Transactional(rollbackFor = Exception.class)
    public MaintWorkOrder accept(Long orderId, OrderAcceptRequest request) {
        MaintWorkOrder before = copyOf(getOrThrow(orderId));
        MaintWorkOrder order = getOrThrow(orderId);
        if (!MaintWorkOrder.STATUS_WORK_DONE.equals(order.getStatus())) {
            throw new IllegalArgumentException("仅完工待验收(WORK_DONE)工单可验收，当前状态：" + order.getStatus());
        }
        boolean approved = Boolean.TRUE.equals(request.getApproved());
        if (approved) {
            order.setStatus(MaintWorkOrder.STATUS_ACCEPTED);
            order.setAcceptedFlag(Boolean.TRUE);
            order.setAcceptedBy(request.getAcceptedBy());
            order.setAcceptedTime(LocalDateTime.now());
            order.setAcceptRemark(request.getRemark());
            order.setRejectReason(null);
            orderMapper.updateById(order);
            evidenceService.record(MaintChangeEvidence.ENTITY_ORDER, order.getId(),
                    MaintChangeEvidence.ACTION_ACCEPT, request.getRequestId(), request.getAcceptedBy(),
                    before, order, "工单验收通过");
        } else {
            if (request.getRemark() == null || request.getRemark().trim().isEmpty()) {
                throw new IllegalArgumentException("验收驳回必须填写原因");
            }
            order.setStatus(MaintWorkOrder.STATUS_REJECTED);
            order.setAcceptedFlag(Boolean.FALSE);
            order.setRejectReason(request.getRemark().trim());
            orderMapper.updateById(order);
            evidenceService.record(MaintChangeEvidence.ENTITY_ORDER, order.getId(),
                    MaintChangeEvidence.ACTION_REJECT, request.getRequestId(), request.getAcceptedBy(),
                    before, order, "工单验收驳回退回返修：" + request.getRemark());
        }
        return order;
    }

    // ------------------------------------------------------------
    // 安全确认 / 恢复运行 / 关闭
    // ------------------------------------------------------------

    @Transactional(rollbackFor = Exception.class)
    public MaintSafetyCheck confirmSafety(Long orderId, SafetyConfirmRequest request) {
        MaintWorkOrder order = getOrThrow(orderId);
        if (!MaintWorkOrder.STATUS_ACCEPTED.equals(order.getStatus())) {
            throw new IllegalArgumentException("仅验收通过(ACCEPTED)工单可做恢复运行安全确认，当前状态："
                    + order.getStatus());
        }
        MaintSafetyCheck exists = safetyMapper.selectOne(new QueryWrapper<MaintSafetyCheck>()
                .eq("order_id", orderId).last("limit 1"));
        if (exists != null) {
            return exists;
        }
        if (!Boolean.TRUE.equals(request.getPowerIsolatedFlag())
                || !Boolean.TRUE.equals(request.getLockoutTagoutFlag())
                || !Boolean.TRUE.equals(request.getRetestOkFlag())) {
            throw new IllegalArgumentException("安全确认未全部通过（断电隔离/上锁挂牌/复检须全部确认），禁止恢复运行");
        }
        MaintSafetyCheck check = new MaintSafetyCheck();
        check.setOrderId(orderId);
        check.setDeviceId(order.getDeviceId());
        check.setPowerIsolatedFlag(Boolean.TRUE);
        check.setLockoutTagoutFlag(Boolean.TRUE);
        check.setRetestOkFlag(Boolean.TRUE);
        check.setConfirmedBy(request.getConfirmedBy());
        check.setConfirmedTime(LocalDateTime.now());
        check.setRequestId(request.getRequestId());
        safetyMapper.insert(check);
        evidenceService.record(MaintChangeEvidence.ENTITY_SAFETY, check.getId(),
                MaintChangeEvidence.ACTION_SAFETY_CONFIRM, request.getRequestId(), request.getConfirmedBy(),
                null, check, "恢复运行前安全确认通过：断电隔离/上锁挂牌/复检全部合格");
        return check;
    }

    /**
     * 关闭工单并恢复设备运行：必须已验收通过且安全确认完成，否则一律拒绝。
     */
    @Transactional(rollbackFor = Exception.class)
    public MaintWorkOrder close(Long orderId, String operator, String requestId, LocalDateTime now) {
        MaintWorkOrder before = copyOf(getOrThrow(orderId));
        MaintWorkOrder order = getOrThrow(orderId);
        if (MaintWorkOrder.STATUS_CLOSED.equals(order.getStatus())) {
            return order;
        }
        if (!Boolean.TRUE.equals(order.getAcceptedFlag())
                || !MaintWorkOrder.STATUS_ACCEPTED.equals(order.getStatus())) {
            throw new IllegalArgumentException("工单未验收通过，不能关闭（当前状态=" + order.getStatus()
                    + "，accepted=" + order.getAcceptedFlag() + "）");
        }
        MaintSafetyCheck safety = safetyMapper.selectOne(new QueryWrapper<MaintSafetyCheck>()
                .eq("order_id", orderId).last("limit 1"));
        if (safety == null || !Boolean.TRUE.equals(safety.getPowerIsolatedFlag())
                || !Boolean.TRUE.equals(safety.getLockoutTagoutFlag())
                || !Boolean.TRUE.equals(safety.getRetestOkFlag())) {
            throw new IllegalArgumentException("设备恢复运行前必须完成并通过安全确认，工单不能关闭");
        }

        order.setStatus(MaintWorkOrder.STATUS_CLOSED);
        order.setOpenFlag(null); // 释放"同设备同模板未关闭唯一"槽位，历史工单保留
        order.setClosedTime(now);
        orderMapper.updateById(order);

        MaintDevice device = deviceService.getOrThrow(order.getDeviceId());
        MaintDevice beforeDevice = copyOfDevice(device);
        BigDecimal completedMeter = device.getMeterHours();
        LocalDate completedDate = now.toLocalDate();
        device.setStatus(MaintDevice.STATUS_RUNNING);
        device.setLastMaintainMeter(completedMeter);
        device.setLastMaintainDate(completedDate);
        deviceServiceApplyStatus(device);

        if (order.getPlanId() != null) {
            MaintPlan plan = planService.getOrThrow(order.getPlanId());
            if (Boolean.TRUE.equals(plan.getActiveFlag())) {
                planService.reanchorAfterClose(plan, completedMeter, completedDate);
            }
        }

        evidenceService.record(MaintChangeEvidence.ENTITY_ORDER, order.getId(),
                MaintChangeEvidence.ACTION_CLOSE, requestId, operator, before, order,
                "工单关闭，设备[" + device.getDeviceCode() + "]恢复运行");
        evidenceService.record(MaintChangeEvidence.ENTITY_DEVICE, device.getId(),
                MaintChangeEvidence.ACTION_RESUME, requestId, operator, beforeDevice, device,
                "安全确认通过后设备恢复RUNNING，保养基准更新为读数" + completedMeter + "/" + completedDate);
        return order;
    }

    // ------------------------------------------------------------
    // 逾期升级
    // ------------------------------------------------------------

    /**
     * 逾期升级扫描：未开工（PLANNED/ASSIGNED）工单超过 dueTime+宽限升一级（HIGH/现场主管），
     * 一级后再过24h仍未开工升二级（URGENT/设备主管）。已到级不重复升级，每次升级留证。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> runEscalationScan(LocalDateTime now) {
        List<MaintWorkOrder> open = orderMapper.selectList(new QueryWrapper<MaintWorkOrder>()
                .eq("open_flag", Boolean.TRUE)
                .in("status", MaintWorkOrder.STATUS_PLANNED, MaintWorkOrder.STATUS_ASSIGNED)
                .orderByAsc("due_time", "id"));
        List<MaintWorkOrder> escalated = new ArrayList<>();
        for (MaintWorkOrder order : open) {
            LocalDateTime level1At = order.getDueTime().plusHours(LEVEL1_GRACE_HOURS);
            LocalDateTime level2At = level1At.plusHours(LEVEL2_AFTER_LEVEL1_HOURS);
            int targetLevel = 0;
            if (order.getEscalationLevel() < 2 && !now.isBefore(level2At)) {
                targetLevel = 2;
            } else if (order.getEscalationLevel() < 1 && !now.isBefore(level1At)) {
                targetLevel = 1;
            }
            if (targetLevel == 0) {
                continue;
            }
            MaintWorkOrder before = copyOf(order);
            order.setEscalationLevel(targetLevel);
            order.setPriority(targetLevel >= 2 ? MaintWorkOrder.PRIORITY_URGENT : MaintWorkOrder.PRIORITY_HIGH);
            order.setEscalatedTime(now);
            orderMapper.updateById(order);
            String supervisor = targetLevel >= 2 ? "设备主管" : "现场主管";
            evidenceService.record(MaintChangeEvidence.ENTITY_ORDER, order.getId(),
                    MaintChangeEvidence.ACTION_ESCALATE, null, "system-scheduler", before, order,
                    "工单逾期未开工，自动升级至" + targetLevel + "级（" + (targetLevel >= 2 ? "URGENT" : "HIGH")
                            + "），通知" + supervisor + "，到期=" + order.getDueTime());
            escalated.add(order);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scanTime", now);
        result.put("escalatedCount", escalated.size());
        result.put("escalated", escalated);
        return result;
    }

    // ------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------

    public MaintWorkOrder getOrThrow(Long id) {
        MaintWorkOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new IllegalArgumentException("保养工单不存在，id=" + id);
        }
        return order;
    }

    public List<MaintWorkOrder> listOrders(Long deviceId, String status, Long planId) {
        QueryWrapper<MaintWorkOrder> wrapper = new QueryWrapper<>();
        wrapper.eq(deviceId != null, "device_id", deviceId);
        wrapper.eq(status != null && !status.trim().isEmpty(), "status", status);
        wrapper.eq(planId != null, "plan_id", planId);
        wrapper.orderByAsc("due_time", "id");
        return orderMapper.selectList(wrapper);
    }

    public Map<String, Object> detail(Long orderId) {
        MaintWorkOrder order = getOrThrow(orderId);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("order", order);
        detail.put("partLines", listLines(orderId));
        detail.put("replacements", replacementMapper.selectList(new QueryWrapper<MaintPartReplacement>()
                .eq("order_id", orderId).orderByAsc("id")));
        detail.put("safetyCheck", safetyMapper.selectOne(new QueryWrapper<MaintSafetyCheck>()
                .eq("order_id", orderId).last("limit 1")));
        return detail;
    }

    public List<MaintPartReplacement> replacementsOf(String serialNo) {
        return replacementMapper.selectList(new QueryWrapper<MaintPartReplacement>()
                .eq("old_serial_no", serialNo).or().eq("new_serial_no", serialNo)
                .orderByAsc("replaced_time", "id"));
    }

    // ------------------------------------------------------------
    // 内部辅助
    // ------------------------------------------------------------

    private boolean planExpired(MaintPlan plan, LocalDate today) {
        return plan.getEndDate() != null && today.isAfter(plan.getEndDate());
    }

    private String generationKey(MaintPlan plan, int occurrence) {
        if (MaintPlan.TRIGGER_HOURS.equals(plan.getTriggerType())) {
            return "H-" + plan.getId() + "-" + occurrence;
        }
        return "D-" + plan.getId() + "-" + occurrence + "-" + plan.getNextDueDate();
    }

    private MaintWorkOrder findOpenOrder(Long deviceId, String template) {
        return orderMapper.selectOne(new QueryWrapper<MaintWorkOrder>()
                .eq("device_id", deviceId)
                .eq("task_template", template)
                .eq("open_flag", Boolean.TRUE)
                .last("limit 1"));
    }

    private MaintOrderPartLine findLine(Long orderId, String partName) {
        MaintOrderPartLine line = lineMapper.selectOne(new QueryWrapper<MaintOrderPartLine>()
                .eq("order_id", orderId).eq("part_name", partName).last("limit 1"));
        if (line == null) {
            throw new IllegalArgumentException("工单无此备件需求：" + partName);
        }
        return line;
    }

    private List<MaintOrderPartLine> listLines(Long orderId) {
        return lineMapper.selectList(new QueryWrapper<MaintOrderPartLine>()
                .eq("order_id", orderId).orderByAsc("id"));
    }

    private void deviceServiceApplyStatus(MaintDevice device) {
        // 复用设备 mapper 的更新路径，避免绕过填充；设备服务未暴露更新方法，此处直接借助其事务内实体
        deviceService.forceUpdate(device);
    }

    private MaintWorkOrder copyOf(MaintWorkOrder source) {
        MaintWorkOrder copy = new MaintWorkOrder();
        org.springframework.beans.BeanUtils.copyProperties(source, copy);
        return copy;
    }

    private MaintDevice copyOfDevice(MaintDevice source) {
        MaintDevice copy = new MaintDevice();
        org.springframework.beans.BeanUtils.copyProperties(source, copy);
        return copy;
    }

    private MaintSparePart copyOfPart(MaintSparePart source) {
        MaintSparePart copy = new MaintSparePart();
        org.springframework.beans.BeanUtils.copyProperties(source, copy);
        return copy;
    }

    private MaintOrderPartLine copyOfLine(MaintOrderPartLine source) {
        MaintOrderPartLine copy = new MaintOrderPartLine();
        org.springframework.beans.BeanUtils.copyProperties(source, copy);
        return copy;
    }
}
