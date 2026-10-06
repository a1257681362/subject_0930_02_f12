package com.evops.aqua.maint;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.maint.dto.CrewCreateRequest;
import com.evops.aqua.maint.dto.MaintDeviceRegisterRequest;
import com.evops.aqua.maint.dto.MeterReadingRequest;
import com.evops.aqua.maint.dto.OrderAcceptRequest;
import com.evops.aqua.maint.dto.OrderAssignRequest;
import com.evops.aqua.maint.dto.OrderCompleteRequest;
import com.evops.aqua.maint.dto.OrderGenerateRequest;
import com.evops.aqua.maint.dto.PartReplaceRequest;
import com.evops.aqua.maint.dto.PlanAdjustRequest;
import com.evops.aqua.maint.dto.MaintPlanCreateRequest;
import com.evops.aqua.maint.dto.SafetyConfirmRequest;
import com.evops.aqua.maint.dto.SparePartInRequest;
import com.evops.aqua.maint.dto.WindowCreateRequest;
import com.evops.aqua.maint.entity.MaintConcurrencyReject;
import com.evops.aqua.maint.entity.MaintDevice;
import com.evops.aqua.maint.entity.MaintOpClaim;
import com.evops.aqua.maint.entity.MaintPartReplacement;
import com.evops.aqua.maint.entity.MaintPlan;
import com.evops.aqua.maint.entity.MaintSafetyCheck;
import com.evops.aqua.maint.entity.MaintShutdownWindow;
import com.evops.aqua.maint.entity.MaintSparePart;
import com.evops.aqua.maint.entity.MaintWorkOrder;
import com.evops.aqua.maint.mapper.MaintConcurrencyRejectMapper;
import com.evops.aqua.maint.mapper.MaintOpClaimMapper;
import com.evops.aqua.maint.mapper.MaintPartReplacementMapper;
import com.evops.aqua.maint.mapper.MaintPlanMapper;
import com.evops.aqua.maint.mapper.MaintShutdownWindowMapper;
import com.evops.aqua.maint.mapper.MaintSparePartMapper;
import com.evops.aqua.maint.mapper.MaintWorkOrderMapper;
import com.evops.aqua.maint.service.ConcurrentOperationRejectedException;
import com.evops.aqua.maint.service.MaintCrewService;
import com.evops.aqua.maint.service.MaintDeviceService;
import com.evops.aqua.maint.service.MaintOperationHook;
import com.evops.aqua.maint.service.MaintPlanFacade;
import com.evops.aqua.maint.service.MaintSparePartService;
import com.evops.aqua.maint.service.MaintWindowFacade;
import com.evops.aqua.maint.service.MaintWorkOrderFacade;
import com.evops.aqua.maint.service.MaintWorkOrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 增氧机/泵站/投饵机保养工单验收。
 * 主线：设备建档与运行小时 → HOURS/CALENDAR 双触发计划生成工单
 * → 工单关联停机窗口+备件+维修人员 → 开工/换件保留旧件序列号 → 完工 →
 * 验收驳回与通过 → 未验收不能关闭、安全确认不通过不能恢复运行 → 关闭重锚计划；
 * 逾期自动升级（一级/二级，不重复）；计划调整只影响未生成工单；
 * 同一操作并发只一生效、余者拒绝并保留审计（谁生效/谁被拒）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class EquipmentMaintenanceAcceptanceTest {

    @Autowired private MaintDeviceService deviceService;
    @Autowired private MaintCrewService crewService;
    @Autowired private MaintSparePartService partService;
    @Autowired private MaintPlanFacade planFacade;
    @Autowired private MaintWindowFacade windowFacade;
    @Autowired private MaintWorkOrderFacade orderFacade;
    @Autowired private MaintWorkOrderService orderService;
    @Autowired private MaintOperationHook hook;
    @Autowired private MaintWorkOrderMapper orderMapper;
    @Autowired private MaintPlanMapper planMapper;
    @Autowired private MaintShutdownWindowMapper windowMapper;
    @Autowired private MaintSparePartMapper partMapper;
    @Autowired private MaintPartReplacementMapper replacementMapper;
    @Autowired private MaintConcurrencyRejectMapper rejectMapper;
    @Autowired private MaintOpClaimMapper claimMapper;

    private final List<String> report = new ArrayList<>();

    private Long aeratorId;
    private Long pumpId;
    private Long feederId;
    private Long crewId;
    private Long hoursPlanId;
    private Long calendarPlanId;
    private Long feederPlanId;
    private Long hoursOrderId;
    private Long pumpOrderId;

    @Test
    void acceptanceMaintenanceWorkOrders() throws Exception {
        cp("CP0", "设备建档：增氧机/泵站/投饵机 + 维修人员 + 在库新备件", () -> {
            aeratorId = registerDevice("M-AER-01", "1号增氧机", MaintDevice.TYPE_AERATOR);
            pumpId = registerDevice("M-PUMP-01", "1号泵站", MaintDevice.TYPE_PUMP_STATION);
            feederId = registerDevice("M-FEED-01", "1号投饵机", MaintDevice.TYPE_FEEDER);
            assertEquals(MaintDevice.STATUS_RUNNING, deviceService.getOrThrow(aeratorId).getStatus());

            CrewCreateRequest crew = new CrewCreateRequest();
            crew.setCrewCode("CREW-01");
            crew.setCrewName("李维修");
            crew.setSkill("机电");
            crew.setOperator("admin");
            crew.setRequestId("crew-1");
            crewId = crewService.create(crew).getId();

            stockIn("叶轮轴承", "N-SN-001", "part-n1");
            MaintSparePart n1 = partService.getBySerialOrThrow("N-SN-001");
            assertEquals(MaintSparePart.STATUS_IN_STOCK, n1.getStatus());
        });

        cp("CP1", "建计划：增氧机按运行小时(100h/提前10h,首到期90h)，泵站按日历(1天/提前1天,今天到期)", () -> {
            MaintPlanCreateRequest hours = new MaintPlanCreateRequest();
            hours.setPlanNo("MP-AER-01");
            hours.setDeviceId(aeratorId);
            hours.setTaskTemplate("增氧机叶轮保养");
            hours.setTriggerType(MaintPlan.TRIGGER_HOURS);
            hours.setIntervalHours(new BigDecimal("100"));
            hours.setLeadHours(new BigDecimal("10"));
            hours.setStartDate(LocalDate.now());
            hours.setPartNames(Arrays.asList("叶轮轴承"));
            hours.setOperator("planer");
            hours.setRequestId("plan-hours-1");
            MaintPlan hp = planFacade.create(hours);
            hoursPlanId = hp.getId();
            assertEquals(0, new BigDecimal("90").compareTo(hp.getNextDueMeter()));

            MaintPlanCreateRequest cal = new MaintPlanCreateRequest();
            cal.setPlanNo("MP-PUMP-01");
            cal.setDeviceId(pumpId);
            cal.setTaskTemplate("泵站机组保养");
            cal.setTriggerType(MaintPlan.TRIGGER_CALENDAR);
            cal.setIntervalDays(1);
            cal.setLeadDays(1);
            cal.setStartDate(LocalDate.now());
            cal.setOperator("planer");
            cal.setRequestId("plan-cal-1");
            MaintPlan cpPlan = planFacade.create(cal);
            calendarPlanId = cpPlan.getId();
            assertEquals(LocalDate.now(), cpPlan.getNextDueDate());
        });

        cp("CP2", "同设备同项目重复建生效计划被拒绝（唯一生效）", () -> {
            MaintPlanCreateRequest dup = new MaintPlanCreateRequest();
            dup.setPlanNo("MP-AER-01-DUP");
            dup.setDeviceId(aeratorId);
            dup.setTaskTemplate("增氧机叶轮保养");
            dup.setTriggerType(MaintPlan.TRIGGER_HOURS);
            dup.setIntervalHours(new BigDecimal("100"));
            dup.setStartDate(LocalDate.now());
            dup.setOperator("planer2");
            dup.setRequestId("plan-hours-dup");
            expectIae(() -> planFacade.create(dup), "已存在生效计划");
        });

        cp("CP3", "未到小时阈值不生成小时工单；日历今天到期先生成；上报90h后生成小时工单；扫描幂等", () -> {
            LocalDateTime now = LocalDateTime.now();
            // 第一次扫描：仅泵站日历到期生成1张，增氧机读数0未到90h不生成
            java.util.Map<String, Object> scan0 = orderService.runGenerateScan(now);
            List<MaintWorkOrder> created0 = (List<MaintWorkOrder>) scan0.get("created");
            assertEquals(1, created0.size(), "首次扫描应仅日历工单1张，实际=" + created0.size());
            assertEquals(MaintPlan.TRIGGER_CALENDAR, created0.get(0).getTriggerSource());
            Long hoursCount0 = orderMapper.selectCount(new QueryWrapper<MaintWorkOrder>()
                    .eq("plan_id", hoursPlanId));
            assertEquals(0L, hoursCount0, "读数未到阈值不得生成小时工单");

            MaintWorkOrder pumpOrder = created0.get(0);
            pumpOrderId = pumpOrder.getId();

            // 先报50h（仍低于90h阈值，不生成）
            reportMeter("meter-50", new BigDecimal("50"));
            // 回退读数49<50 拒绝
            MeterReadingRequest back = new MeterReadingRequest();
            back.setReadingHours(new BigDecimal("49"));
            back.setReporter("值班员");
            back.setRequestId("meter-back");
            expectIae(() -> deviceService.reportReading(aeratorId, back), "不能小于当前累计");

            reportMeter("meter-90", new BigDecimal("90"));
            java.util.Map<String, Object> scan1 = orderService.runGenerateScan(now);
            List<MaintWorkOrder> created1 = (List<MaintWorkOrder>) scan1.get("created");
            assertEquals(1, created1.size(), "到90h应只新增小时工单1张");
            assertEquals(MaintPlan.TRIGGER_HOURS, created1.get(0).getTriggerSource());
            hoursOrderId = created1.get(0).getId();
            assertEquals(MaintWorkOrder.STATUS_PLANNED, orderService.getOrThrow(hoursOrderId).getStatus());

            java.util.Map<String, Object> scan2 = orderService.runGenerateScan(now);
            assertEquals(0, ((List<?>) scan2.get("created")).size(), "重复扫描不得重复生成");
        });

        cp("CP4", "计划调整只影响未生成工单：改泵站间隔后已生成工单到期时间不变", () -> {
            LocalDate dueBefore = orderService.getOrThrow(pumpOrderId).getDueTime().toLocalDate();
            PlanAdjustRequest req = new PlanAdjustRequest();
            req.setIntervalDays(30);
            req.setLeadDays(0);
            req.setOperator("planer");
            req.setRequestId("adjust-pump-1");
            MaintPlan adjusted = planFacade.adjust(calendarPlanId, req);
            assertTrue(adjusted.getNextDueDate().isAfter(LocalDate.now()), "后续排期应推到未来");
            assertEquals(dueBefore, orderService.getOrThrow(pumpOrderId).getDueTime().toLocalDate(),
                    "已生成工单的到期时间不得被计划调整改变");
        });

        cp("CP5", "工单关联停机窗口与维修人员；窗口设备不符被拒；开工后设备转MAINT并快照读数", () -> {
            WindowCreateRequest win = new WindowCreateRequest();
            win.setWindowNo("WIN-AER-01");
            win.setDeviceId(aeratorId);
            win.setStartTime(LocalDateTime.now().minusMinutes(10));
            win.setEndTime(LocalDateTime.now().plusHours(3));
            win.setOperator("调度员");
            win.setRequestId("win-1");
            Long windowId = windowFacade.open(win).getId();

            OrderAssignRequest wrongDevice = new OrderAssignRequest();
            wrongDevice.setWindowId(windowId);
            wrongDevice.setCrewId(crewId);
            wrongDevice.setOperator("调度员");
            expectIae(() -> orderFacade.assign(pumpOrderId, wrongDevice), "设备不一致");

            OrderAssignRequest assign = new OrderAssignRequest();
            assign.setWindowId(windowId);
            assign.setCrewId(crewId);
            assign.setOperator("调度员");
            assign.setRequestId("assign-1");
            MaintWorkOrder assigned = orderFacade.assign(hoursOrderId, assign);
            assertEquals(MaintWorkOrder.STATUS_ASSIGNED, assigned.getStatus());
            assertEquals("李维修", assigned.getAssignedCrewName());
            assertNotNull(assigned.getWindowId());

            MaintWorkOrder started = orderFacade.start(hoursOrderId, "李维修", "start-1");
            assertEquals(MaintWorkOrder.STATUS_IN_PROGRESS, started.getStatus());
            assertEquals(0, new BigDecimal("90").compareTo(started.getMeterSnapshot()));
            assertEquals(MaintDevice.STATUS_MAINT, deviceService.getOrThrow(aeratorId).getStatus());
        });

        cp("CP6", "换件保留旧件序列号：新件装机、旧件序列号只追加留痕，已装机新件不能再用", () -> {
            PartReplaceRequest replace = new PartReplaceRequest();
            replace.setPartName("叶轮轴承");
            replace.setOldSerialNo("OLD-SN-001");
            replace.setNewSerialNo("N-SN-001");
            replace.setOldDisposition(MaintPartReplacement.DISPOSITION_RECYCLED);
            replace.setReplacedBy("李维修");
            replace.setRequestId("replace-1");
            MaintPartReplacement rec = orderFacade.replacePart(hoursOrderId, replace);
            assertEquals("OLD-SN-001", rec.getOldSerialNo());
            assertEquals("N-SN-001", rec.getNewSerialNo());
            assertEquals(MaintPartReplacement.DISPOSITION_RECYCLED, rec.getOldDisposition());

            MaintSparePart newPart = partService.getBySerialOrThrow("N-SN-001");
            assertEquals(MaintSparePart.STATUS_INSTALLED, newPart.getStatus());
            assertEquals(aeratorId, newPart.getInstalledDeviceId());

            PartReplaceRequest reuse = new PartReplaceRequest();
            reuse.setPartName("叶轮轴承");
            reuse.setOldSerialNo("OLD-SN-002");
            reuse.setNewSerialNo("N-SN-001");
            reuse.setReplacedBy("李维修");
            reuse.setRequestId("replace-2");
            expectIae(() -> orderFacade.replacePart(hoursOrderId, reuse), "非在库");

            List<MaintPartReplacement> lineage = orderService.replacementsOf("OLD-SN-001");
            assertEquals(1, lineage.size(), "旧件序列号谱系可追溯且仅一条");
        });

        cp("CP7", "完工→验收驳回(须填原因)退回返修→再完工→验收通过；未验收不能关闭", () -> {
            orderFacade.complete(hoursOrderId, complete("李维修", "complete-1"));
            assertEquals(MaintWorkOrder.STATUS_WORK_DONE, orderService.getOrThrow(hoursOrderId).getStatus());

            OrderAcceptRequest noReason = accept(false, "王主管", null, "accept-noreason");
            expectIae(() -> orderFacade.accept(hoursOrderId, noReason), "驳回必须填写原因");

            MaintWorkOrder rejected = orderFacade.accept(
                    hoursOrderId, accept(false, "王主管", "轴承异响未排除", "accept-reject"));
            assertEquals(MaintWorkOrder.STATUS_REJECTED, rejected.getStatus());
            assertFalse(rejected.getAcceptedFlag());
            expectIae(() -> orderFacade.close(hoursOrderId, "王主管", "close-early"), "未验收");

            orderFacade.complete(hoursOrderId, complete("李维修", "complete-2"));
            MaintWorkOrder accepted = orderFacade.accept(
                    hoursOrderId, accept(true, "王主管", "合格", "accept-ok"));
            assertEquals(MaintWorkOrder.STATUS_ACCEPTED, accepted.getStatus());
            assertTrue(accepted.getAcceptedFlag());
        });

        cp("CP8", "恢复运行前必须安全确认：缺项拒绝不落单，三项全通过后关闭并恢复RUNNING", () -> {
            expectIae(() -> orderFacade.close(hoursOrderId, "王主管", "close-nosafety"), "安全确认");

            SafetyConfirmRequest notAll = new SafetyConfirmRequest();
            notAll.setPowerIsolatedFlag(Boolean.TRUE);
            notAll.setLockoutTagoutFlag(Boolean.FALSE);
            notAll.setRetestOkFlag(Boolean.TRUE);
            notAll.setConfirmedBy("安全员");
            notAll.setRequestId("safety-fail");
            expectIae(() -> orderFacade.confirmSafety(hoursOrderId, notAll), "安全确认未全部通过");
            assertNull(currentSafety(), "未通过不得落安全确认单");

            SafetyConfirmRequest ok = new SafetyConfirmRequest();
            ok.setPowerIsolatedFlag(Boolean.TRUE);
            ok.setLockoutTagoutFlag(Boolean.TRUE);
            ok.setRetestOkFlag(Boolean.TRUE);
            ok.setConfirmedBy("安全员");
            ok.setRequestId("safety-ok");
            MaintSafetyCheck check = orderFacade.confirmSafety(hoursOrderId, ok);
            assertNotNull(check.getId());

            MaintWorkOrder closed = orderFacade.close(hoursOrderId, "王主管", "close-ok");
            assertEquals(MaintWorkOrder.STATUS_CLOSED, closed.getStatus());
            assertNull(closed.getOpenFlag(), "关闭后释放未关闭唯一槽位");
            MaintDevice device = deviceService.getOrThrow(aeratorId);
            assertEquals(MaintDevice.STATUS_RUNNING, device.getStatus());
            assertEquals(0, new BigDecimal("90").compareTo(device.getLastMaintainMeter()));
            // 关闭按完工读数90重锚：下一期到期 = 90 + 100 - 10 = 180
            MaintPlan plan = planMapper.selectById(hoursPlanId);
            assertEquals(0, new BigDecimal("180").compareTo(plan.getNextDueMeter()),
                    "关闭后应按完工重锚，实际=" + plan.getNextDueMeter());
        });

        cp("CP9", "重锚后到180h生成第2期工单；历史第1期关闭保留", () -> {
            reportMeter("meter-180", new BigDecimal("180"));
            orderService.runGenerateScan(LocalDateTime.now());
            Long openCount = orderMapper.selectCount(new QueryWrapper<MaintWorkOrder>()
                    .eq("device_id", aeratorId)
                    .eq("task_template", "增氧机叶轮保养")
                    .eq("open_flag", Boolean.TRUE));
            assertEquals(1L, openCount, "到180h应生成第2期未关闭工单");
            List<MaintWorkOrder> all = orderMapper.selectList(new QueryWrapper<MaintWorkOrder>()
                    .eq("device_id", aeratorId).orderByAsc("id"));
            assertEquals(2, all.size(), "第1期已关闭保留 + 第2期新建");
            assertEquals(MaintWorkOrder.STATUS_CLOSED, all.get(0).getStatus());
        });

        cp("CP10", "逾期自动升级：泵站日历工单先一级(HIGH/现场主管)后二级(URGENT/设备主管)，不重复", () -> {
            LocalDateTime due = orderService.getOrThrow(pumpOrderId).getDueTime();
            boolean[] hit = new boolean[1];

            runEscalationAndCheck(due.plusHours(3), pumpOrderId, 1, MaintWorkOrder.PRIORITY_HIGH, hit);
            // 同时刻再扫：该工单不再被升级
            java.util.Map<String, Object> again = orderService.runEscalationScan(due.plusHours(3));
            List<MaintWorkOrder> againList = (List<MaintWorkOrder>) again.get("escalated");
            assertTrue(againList.stream().noneMatch(o -> o.getId().equals(pumpOrderId)), "已升级不重复");
            runEscalationAndCheck(due.plusHours(27), pumpOrderId, 2, MaintWorkOrder.PRIORITY_URGENT, hit);
            java.util.Map<String, Object> lvl2Again = orderService.runEscalationScan(due.plusHours(28));
            List<MaintWorkOrder> lvl2List = (List<MaintWorkOrder>) lvl2Again.get("escalated");
            assertTrue(lvl2List.stream().noneMatch(o -> o.getId().equals(pumpOrderId)), "二级后不得重复升级");
            assertTrue(hit[0]);
        });

        cp("CP11", "真实并发：同设备并发开放重叠停机窗口——一生效一拒绝并审计", () -> {
            hook.reset();
            hook.arm();
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                CountDownLatch rejectedObserved = new CountDownLatch(1);
                Future<?> winner = pool.submit(() ->
                        windowFacade.open(window("WIN-CON-A", feederId, "调度甲", "win-con-a")));
                hook.awaitAcquired();

                Future<?> loser = pool.submit(() -> {
                    try {
                        windowFacade.open(window("WIN-CON-B", feederId, "调度乙", "win-con-b"));
                        fail("并发请求乙应当被拒绝");
                    } catch (ConcurrentOperationRejectedException ex) {
                        assertEquals("调度甲", ex.getWinnerOperator());
                        rejectedObserved.countDown();
                    }
                });
                loser.get(10, TimeUnit.SECONDS);
                assertTrue(rejectedObserved.await(5, TimeUnit.SECONDS), "乙应观测到冲突且胜者为甲");
                hook.release();
                winner.get(10, TimeUnit.SECONDS);

                Long openCount = windowMapper.selectCount(new QueryWrapper<MaintShutdownWindow>()
                        .eq("device_id", feederId).eq("status", MaintShutdownWindow.STATUS_OPEN));
                assertEquals(1L, openCount, "并发开放只应一生效一张窗口");
                List<MaintConcurrencyReject> rejects = rejectMapper.selectList(
                        new QueryWrapper<MaintConcurrencyReject>()
                                .eq("operation", "WINDOW_OPEN")
                                .eq("target_id", String.valueOf(feederId)));
                assertEquals(1, rejects.size(), "应有一条拒单审计");
                MaintConcurrencyReject reject = rejects.get(0);
                assertEquals("调度甲", reject.getWinnerOperator(), "审计须记载生效者");
                assertEquals("调度乙", reject.getLoserOperator(), "审计须记载被拒者");
                assertEquals("win-con-a", reject.getWinnerRequestId());
                assertTrue(reject.getReason().contains("被拒绝"));

                Long activeClaims = claimMapper.selectCount(new QueryWrapper<MaintOpClaim>()
                        .eq("active_flag", Boolean.TRUE));
                assertEquals(0L, activeClaims, "业务结束后占位必须释放");
            } finally {
                hook.reset();
                pool.shutdownNow();
            }
        });

        cp("CP12", "真实并发：同计划并发生成工单——一生效一拒绝，未关闭工单仅一张", () -> {
            MaintPlanCreateRequest fp = new MaintPlanCreateRequest();
            fp.setPlanNo("MP-FEED-01");
            fp.setDeviceId(feederId);
            fp.setTaskTemplate("投饵机料线保养");
            fp.setTriggerType(MaintPlan.TRIGGER_CALENDAR);
            fp.setIntervalDays(1);
            fp.setLeadDays(1);
            fp.setStartDate(LocalDate.now());
            fp.setOperator("planer");
            fp.setRequestId("plan-feed-1");
            feederPlanId = planFacade.create(fp).getId();

            hook.reset();
            hook.arm();
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                Future<?> winner = pool.submit(() ->
                        orderFacade.generate(gen(feederPlanId, "班长甲", "gen-a")));
                hook.awaitAcquired();
                final boolean[] rejected = {false};
                Future<?> loser = pool.submit(() -> {
                    try {
                        orderFacade.generate(gen(feederPlanId, "班长乙", "gen-b"));
                        fail("并发生成应当只一生效");
                    } catch (ConcurrentOperationRejectedException ex) {
                        assertEquals("班长甲", ex.getWinnerOperator());
                        rejected[0] = true;
                    }
                });
                loser.get(10, TimeUnit.SECONDS);
                assertTrue(rejected[0], "乙必须被拒绝");
                hook.release();
                winner.get(10, TimeUnit.SECONDS);

                Long openCount = orderMapper.selectCount(new QueryWrapper<MaintWorkOrder>()
                        .eq("plan_id", feederPlanId).eq("open_flag", Boolean.TRUE));
                assertEquals(1L, openCount, "并发生成只允许一张未关闭工单");
                List<MaintConcurrencyReject> rejects = rejectMapper.selectList(
                        new QueryWrapper<MaintConcurrencyReject>()
                                .eq("operation", "ORDER_GENERATE")
                                .eq("target_id", "PLAN:" + feederPlanId));
                assertEquals(1, rejects.size());
                assertEquals("班长甲", rejects.get(0).getWinnerOperator());
                assertEquals("班长乙", rejects.get(0).getLoserOperator());
            } finally {
                hook.reset();
                pool.shutdownNow();
            }
        });

        cp("CP13", "审计完整性：换件只追加、并发拒单留痕、关键状态变更均有证据", () -> {
            Long repCount = replacementMapper.selectCount(new QueryWrapper<MaintPartReplacement>()
                    .eq("old_serial_no", "OLD-SN-001"));
            assertEquals(1L, repCount, "旧件序列号换件记录只追加一条");
            Long allRejects = rejectMapper.selectCount(new QueryWrapper<MaintConcurrencyReject>());
            assertTrue(allRejects >= 2L, "并发拒单审计应至少两条，实际=" + allRejects);
            System.out.println("[验收] 并发冲突处理结论：生效者落业务数据，被拒者落拒单审计（记载双方与原因），证据链完整");
        });

        printReport();
    }

    // ------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------

    private void runEscalationAndCheck(LocalDateTime scanTime, Long orderId, int level, String priority,
                                       boolean[] hit) {
        java.util.Map<String, Object> r = orderService.runEscalationScan(scanTime);
        List<MaintWorkOrder> escalated = (List<MaintWorkOrder>) r.get("escalated");
        boolean found = escalated.stream().anyMatch(o -> o.getId().equals(orderId));
        assertTrue(found, "泵站工单应在本次扫描升级到" + level + "级");
        MaintWorkOrder o = orderService.getOrThrow(orderId);
        assertEquals(level, o.getEscalationLevel());
        assertEquals(priority, o.getPriority());
        hit[0] = true;
    }

    private MaintSafetyCheck currentSafety() {
        Object safety = orderService.detail(hoursOrderId).get("safetyCheck");
        return safety instanceof MaintSafetyCheck ? (MaintSafetyCheck) safety : null;
    }

    private void reportMeter(String requestId, BigDecimal hours) {
        MeterReadingRequest r = new MeterReadingRequest();
        r.setReadingHours(hours);
        r.setReporter("值班员");
        r.setRequestId(requestId);
        deviceService.reportReading(aeratorId, r);
    }

    private Long registerDevice(String code, String name, String type) {
        MaintDeviceRegisterRequest req = new MaintDeviceRegisterRequest();
        req.setDeviceCode(code);
        req.setDeviceName(name);
        req.setDeviceType(type);
        req.setLastMaintainDate(LocalDate.now());
        req.setRequestId("dev-" + code);
        return deviceService.register(req).getId();
    }

    private void stockIn(String name, String serial, String requestId) {
        SparePartInRequest req = new SparePartInRequest();
        req.setPartName(name);
        req.setSerialNo(serial);
        req.setOperator("仓管");
        req.setRequestId(requestId);
        partService.stockIn(req);
    }

    private WindowCreateRequest window(String no, Long deviceId, String operator, String requestId) {
        WindowCreateRequest w = new WindowCreateRequest();
        w.setWindowNo(no);
        w.setDeviceId(deviceId);
        w.setStartTime(LocalDateTime.now().minusMinutes(5));
        w.setEndTime(LocalDateTime.now().plusHours(2));
        w.setOperator(operator);
        w.setRequestId(requestId);
        return w;
    }

    private OrderGenerateRequest gen(Long planId, String operator, String requestId) {
        OrderGenerateRequest g = new OrderGenerateRequest();
        g.setPlanId(planId);
        g.setOperator(operator);
        g.setRequestId(requestId);
        return g;
    }

    private OrderCompleteRequest complete(String operator, String requestId) {
        OrderCompleteRequest c = new OrderCompleteRequest();
        c.setOperator(operator);
        c.setRequestId(requestId);
        return c;
    }

    private OrderAcceptRequest accept(boolean approved, String by, String remark, String requestId) {
        OrderAcceptRequest a = new OrderAcceptRequest();
        a.setApproved(approved);
        a.setAcceptedBy(by);
        a.setRemark(remark);
        a.setRequestId(requestId);
        return a;
    }

    private void expectIae(RunnableThrowing body, String messagePart) {
        try {
            body.run();
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage() != null && ex.getMessage().contains(messagePart),
                    "异常信息应包含[" + messagePart + "]，实际=" + ex.getMessage());
            return;
        } catch (Exception ex) {
            fail("应抛IllegalArgumentException，实际抛出" + ex.getClass().getSimpleName()
                    + "：" + ex.getMessage());
            return;
        }
        fail("应当抛出包含[" + messagePart + "]的IllegalArgumentException");
    }

    private void cp(String id, String name, RunnableThrowing body) {
        try {
            body.run();
            String line = "通过 " + id + " " + name;
            report.add(line);
            System.out.println("[验收] " + line);
        } catch (Throwable t) {
            report.add("停点 " + id + "「" + name + "」失败：" + t);
            printReport();
            fail("验收停点 " + id + "：" + t.getMessage());
        }
    }

    private void printReport() {
        StringBuilder sb = new StringBuilder("\n========== 设备保养工单验收报告 ==========\n");
        for (String line : report) {
            sb.append(line).append('\n');
        }
        sb.append("===========================================\n");
        System.out.println(sb);
    }

    private interface RunnableThrowing {
        void run() throws Exception;
    }
}
