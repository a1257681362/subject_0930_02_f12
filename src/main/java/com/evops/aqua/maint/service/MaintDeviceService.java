package com.evops.aqua.maint.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.maint.dto.MaintDeviceRegisterRequest;
import com.evops.aqua.maint.dto.MeterReadingRequest;
import com.evops.aqua.maint.entity.MaintChangeEvidence;
import com.evops.aqua.maint.entity.MaintDevice;
import com.evops.aqua.maint.entity.MaintMeterReading;
import com.evops.aqua.maint.mapper.MaintDeviceMapper;
import com.evops.aqua.maint.mapper.MaintMeterReadingMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 设备档案与运行小时：登记幂等（requestId/编码）；读数只追加且单调不减；
 * 读数达到 HOURS 计划阈值由工单调度扫描生成工单。
 */
@Service
public class MaintDeviceService {

    private final MaintDeviceMapper deviceMapper;
    private final MaintMeterReadingMapper readingMapper;
    private final MaintEvidenceService evidenceService;

    public MaintDeviceService(MaintDeviceMapper deviceMapper,
                              MaintMeterReadingMapper readingMapper,
                              MaintEvidenceService evidenceService) {
        this.deviceMapper = deviceMapper;
        this.readingMapper = readingMapper;
        this.evidenceService = evidenceService;
    }

    @Transactional(rollbackFor = Exception.class)
    public MaintDevice register(MaintDeviceRegisterRequest request) {
        if (request.getRequestId() != null && !request.getRequestId().trim().isEmpty()) {
            List<MaintDevice> same = deviceMapper.selectList(
                    new QueryWrapper<MaintDevice>().eq("request_id", request.getRequestId()));
            if (!same.isEmpty()) {
                return same.get(0);
            }
        }
        requireValidType(request.getDeviceType());
        Long exists = deviceMapper.selectCount(
                new QueryWrapper<MaintDevice>().eq("device_code", request.getDeviceCode()));
        if (exists != null && exists > 0) {
            throw new IllegalArgumentException("设备编码已存在：" + request.getDeviceCode());
        }
        MaintDevice device = new MaintDevice();
        device.setDeviceCode(request.getDeviceCode());
        device.setDeviceName(request.getDeviceName());
        device.setDeviceType(request.getDeviceType());
        device.setPondId(request.getPondId());
        device.setStatus(MaintDevice.STATUS_RUNNING);
        device.setMeterHours(BigDecimal.ZERO);
        device.setLastMaintainMeter(BigDecimal.ZERO);
        device.setLastMaintainDate(request.getLastMaintainDate());
        device.setRequestId(request.getRequestId());
        device.setRemark(request.getRemark());
        deviceMapper.insert(device);
        evidenceService.record(MaintChangeEvidence.ENTITY_DEVICE, device.getId(),
                MaintChangeEvidence.ACTION_CREATE, request.getRequestId(), "system", null, device,
                "登记" + typeName(request.getDeviceType()) + "设备[" + device.getDeviceCode() + "]");
        return device;
    }

    /**
     * 上报运行小时。幂等：相同 requestId 重复上报返回首次流水。
     * 读数不得小于设备当前累计小时数（回退拒绝），读数流水只追加。
     */
    @Transactional(rollbackFor = Exception.class)
    public MaintMeterReading reportReading(Long deviceId, MeterReadingRequest request) {
        MaintMeterReading same = readingMapper.selectOne(new QueryWrapper<MaintMeterReading>()
                .eq("request_id", request.getRequestId()).last("limit 1"));
        if (same != null) {
            return same;
        }
        MaintDevice device = getOrThrow(deviceId);
        if (request.getReadingHours().compareTo(device.getMeterHours()) < 0) {
            throw new IllegalArgumentException("运行小时读数不能小于当前累计值：当前="
                    + device.getMeterHours() + "，上报=" + request.getReadingHours());
        }
        MaintMeterReading reading = new MaintMeterReading();
        reading.setDeviceId(deviceId);
        reading.setReadingHours(request.getReadingHours());
        reading.setReadingTime(request.getReadingTime() == null ? LocalDateTime.now() : request.getReadingTime());
        reading.setReporter(request.getReporter());
        reading.setRequestId(request.getRequestId());
        readingMapper.insert(reading);

        MaintDevice before = copyOf(device);
        device.setMeterHours(request.getReadingHours());
        deviceMapper.updateById(device);
        evidenceService.record(MaintChangeEvidence.ENTITY_METER, reading.getId(),
                MaintChangeEvidence.ACTION_CREATE, request.getRequestId(), request.getReporter(),
                null, reading, "设备[" + device.getDeviceCode() + "]运行小时上报："
                        + before.getMeterHours() + "→" + request.getReadingHours());
        return reading;
    }

    /** 供工单服务在同一事务内更新设备状态/保养基准。 */
    public void forceUpdate(MaintDevice device) {
        deviceMapper.updateById(device);
    }

    public MaintDevice getOrThrow(Long id) {
        MaintDevice device = deviceMapper.selectById(id);
        if (device == null) {
            throw new IllegalArgumentException("设备不存在，id=" + id);
        }
        return device;
    }

    public List<MaintDevice> list(String deviceType, String status) {
        QueryWrapper<MaintDevice> wrapper = new QueryWrapper<>();
        wrapper.eq(deviceType != null && !deviceType.trim().isEmpty(), "device_type", deviceType);
        wrapper.eq(status != null && !status.trim().isEmpty(), "status", status);
        wrapper.orderByAsc("id");
        return deviceMapper.selectList(wrapper);
    }

    static void requireValidType(String type) {
        if (!MaintDevice.TYPE_AERATOR.equals(type)
                && !MaintDevice.TYPE_PUMP_STATION.equals(type)
                && !MaintDevice.TYPE_FEEDER.equals(type)) {
            throw new IllegalArgumentException("设备类型非法（AERATOR/PUMP_STATION/FEEDER）：" + type);
        }
    }

    static String typeName(String type) {
        if (MaintDevice.TYPE_AERATOR.equals(type)) {
            return "增氧机";
        }
        if (MaintDevice.TYPE_PUMP_STATION.equals(type)) {
            return "泵站";
        }
        if (MaintDevice.TYPE_FEEDER.equals(type)) {
            return "投饵机";
        }
        return type;
    }

    private MaintDevice copyOf(MaintDevice source) {
        MaintDevice copy = new MaintDevice();
        org.springframework.beans.BeanUtils.copyProperties(source, copy);
        return copy;
    }
}
