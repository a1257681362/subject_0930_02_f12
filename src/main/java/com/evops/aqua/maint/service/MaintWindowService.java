package com.evops.aqua.maint.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.maint.dto.WindowCreateRequest;
import com.evops.aqua.maint.entity.MaintChangeEvidence;
import com.evops.aqua.maint.entity.MaintDevice;
import com.evops.aqua.maint.entity.MaintShutdownWindow;
import com.evops.aqua.maint.mapper.MaintShutdownWindowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 停机窗口：同设备时间重叠的窗口不能重复开放；工单开工时点必须落在窗口区间内。
 * 并发开放由 {@link MaintWindowFacade} 经并发仲裁串行化——只一生效，余者拒绝并审计。
 */
@Service
public class MaintWindowService {

    private final MaintShutdownWindowMapper windowMapper;
    private final MaintDeviceService deviceService;
    private final MaintEvidenceService evidenceService;

    public MaintWindowService(MaintShutdownWindowMapper windowMapper,
                              MaintDeviceService deviceService,
                              MaintEvidenceService evidenceService) {
        this.windowMapper = windowMapper;
        this.deviceService = deviceService;
        this.evidenceService = evidenceService;
    }

    @Transactional(rollbackFor = Exception.class)
    public MaintShutdownWindow create(WindowCreateRequest request) {
        MaintDevice device = deviceService.getOrThrow(request.getDeviceId());
        if (!request.getEndTime().isAfter(request.getStartTime())) {
            throw new IllegalArgumentException("停机窗口结束时间必须晚于开始时间");
        }
        Long noExists = windowMapper.selectCount(
                new QueryWrapper<MaintShutdownWindow>().eq("window_no", request.getWindowNo()));
        if (noExists != null && noExists > 0) {
            throw new IllegalArgumentException("停机窗口编号已存在：" + request.getWindowNo());
        }
        List<MaintShutdownWindow> openWindows = windowMapper.selectList(new QueryWrapper<MaintShutdownWindow>()
                .eq("device_id", device.getId())
                .eq("status", MaintShutdownWindow.STATUS_OPEN));
        for (MaintShutdownWindow open : openWindows) {
            if (overlaps(open.getStartTime(), open.getEndTime(), request.getStartTime(), request.getEndTime())) {
                throw new IllegalArgumentException("设备[" + device.getDeviceCode() + "]在 "
                        + open.getStartTime() + "~" + open.getEndTime()
                        + " 已有开放停机窗口[" + open.getWindowNo() + "]，时间重叠不能重复开放");
            }
        }
        MaintShutdownWindow window = new MaintShutdownWindow();
        window.setWindowNo(request.getWindowNo());
        window.setDeviceId(device.getId());
        window.setStartTime(request.getStartTime());
        window.setEndTime(request.getEndTime());
        window.setStatus(MaintShutdownWindow.STATUS_OPEN);
        window.setNativeTime(LocalDateTime.now());
        window.setRequestId(request.getRequestId());
        window.setRemark(request.getRemark());
        windowMapper.insert(window);
        evidenceService.record(MaintChangeEvidence.ENTITY_WINDOW, window.getId(),
                MaintChangeEvidence.ACTION_CREATE, request.getRequestId(), request.getOperator(),
                null, window, "开放停机窗口[" + window.getWindowNo() + "]：设备["
                        + device.getDeviceCode() + "] " + window.getStartTime() + "~" + window.getEndTime());
        return window;
    }

    @Transactional(rollbackFor = Exception.class)
    public MaintShutdownWindow close(Long windowId, String operator, String requestId) {
        MaintShutdownWindow window = getOrThrow(windowId);
        if (MaintShutdownWindow.STATUS_CLOSED.equals(window.getStatus())) {
            return window;
        }
        MaintShutdownWindow before = copyOf(window);
        window.setStatus(MaintShutdownWindow.STATUS_CLOSED);
        windowMapper.updateById(window);
        evidenceService.record(MaintChangeEvidence.ENTITY_WINDOW, window.getId(),
                MaintChangeEvidence.ACTION_CLOSE, requestId, operator, before, window,
                "关闭停机窗口[" + window.getWindowNo() + "]");
        return window;
    }

    public MaintShutdownWindow getOrThrow(Long id) {
        MaintShutdownWindow window = windowMapper.selectById(id);
        if (window == null) {
            throw new IllegalArgumentException("停机窗口不存在，id=" + id);
        }
        return window;
    }

    public List<MaintShutdownWindow> list(Long deviceId, String status) {
        QueryWrapper<MaintShutdownWindow> wrapper = new QueryWrapper<>();
        wrapper.eq(deviceId != null, "device_id", deviceId);
        wrapper.eq(status != null && !status.trim().isEmpty(), "status", status);
        wrapper.orderByAsc("start_time", "id");
        return windowMapper.selectList(wrapper);
    }

    static boolean overlaps(LocalDateTime s1, LocalDateTime e1, LocalDateTime s2, LocalDateTime e2) {
        return s1.isBefore(e2) && s2.isBefore(e1);
    }

    private MaintShutdownWindow copyOf(MaintShutdownWindow source) {
        MaintShutdownWindow copy = new MaintShutdownWindow();
        org.springframework.beans.BeanUtils.copyProperties(source, copy);
        return copy;
    }
}
