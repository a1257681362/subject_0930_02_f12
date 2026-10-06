package com.evops.aqua.maint.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.maint.entity.MaintConcurrencyReject;
import com.evops.aqua.maint.entity.MaintOpClaim;
import com.evops.aqua.maint.mapper.MaintConcurrencyRejectMapper;
import com.evops.aqua.maint.mapper.MaintOpClaimMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 并发占位的独立事务操作：独立成 Bean 以确保 {@code REQUIRES_NEW} 经代理生效
 * （抢占/释放/拒单留痕必须与业务事务隔离，业务回滚不影响占位与审计）。
 */
@Service
public class MaintClaimTxService {

    private static final Logger log = LoggerFactory.getLogger(MaintClaimTxService.class);
    private static final int STALE_MINUTES = 10;

    private final MaintOpClaimMapper claimMapper;
    private final MaintConcurrencyRejectMapper rejectMapper;

    public MaintClaimTxService(MaintOpClaimMapper claimMapper, MaintConcurrencyRejectMapper rejectMapper) {
        this.claimMapper = claimMapper;
        this.rejectMapper = rejectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public MaintConcurrencyService.ClaimAcquire tryAcquire(String operation, String targetType, String targetId,
                                                           String operator, String requestId) {
        MaintOpClaim claim = new MaintOpClaim();
        claim.setOperation(operation);
        claim.setTargetType(targetType);
        claim.setTargetId(targetId);
        claim.setActiveFlag(Boolean.TRUE);
        claim.setHolderOperator(operator);
        claim.setHolderRequestId(requestId == null ? ("req-" + System.nanoTime()) : requestId);
        claim.setClaimTime(LocalDateTime.now());
        try {
            claimMapper.insert(claim);
            return MaintConcurrencyService.ClaimAcquire.acquired(
                    claim.getId(), claim.getHolderOperator(), claim.getHolderRequestId());
        } catch (DuplicateKeyException dup) {
            MaintOpClaim holder = claimMapper.selectOne(new QueryWrapper<MaintOpClaim>()
                    .eq("operation", operation)
                    .eq("target_type", targetType)
                    .eq("target_id", targetId)
                    .eq("active_flag", Boolean.TRUE)
                    .last("limit 1"));
            if (holder == null) {
                return MaintConcurrencyService.ClaimAcquire.rejected(operator, requestId);
            }
            if (holder.getClaimTime() != null
                    && holder.getClaimTime().isBefore(LocalDateTime.now().minusMinutes(STALE_MINUTES))) {
                holder.setActiveFlag(Boolean.FALSE);
                claimMapper.updateById(holder);
                try {
                    claimMapper.insert(claim);
                    log.warn("接管僵死并发占位 op={} target={}:{}，原持有者={}",
                            operation, targetType, targetId, holder.getHolderOperator());
                    return MaintConcurrencyService.ClaimAcquire.acquired(
                            claim.getId(), operator, claim.getHolderRequestId());
                } catch (DuplicateKeyException again) {
                    return MaintConcurrencyService.ClaimAcquire.rejected(
                            holder.getHolderOperator(), holder.getHolderRequestId());
                }
            }
            return MaintConcurrencyService.ClaimAcquire.rejected(
                    holder.getHolderOperator(), holder.getHolderRequestId());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void release(Long claimId) {
        MaintOpClaim claim = claimMapper.selectById(claimId);
        if (claim != null && Boolean.TRUE.equals(claim.getActiveFlag())) {
            claim.setActiveFlag(null);
            claim.setReleasedTime(LocalDateTime.now());
            claimMapper.updateById(claim);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void recordReject(String operation, String targetType, String targetId,
                             String loserOperator, String loserRequestId,
                             String winnerOperator, String winnerRequestId, String actionDesc) {
        MaintConcurrencyReject reject = new MaintConcurrencyReject();
        reject.setOperation(operation);
        reject.setTargetType(targetType);
        reject.setTargetId(targetId);
        reject.setWinnerOperator(winnerOperator);
        reject.setWinnerRequestId(winnerRequestId);
        reject.setLoserOperator(loserOperator);
        reject.setLoserRequestId(loserRequestId);
        reject.setReason("相同操作并发：[" + winnerOperator + "]的请求已生效（requestId=" + winnerRequestId
                + "），[" + loserOperator + "]的请求被拒绝，操作=" + actionDesc);
        reject.setRejectedTime(LocalDateTime.now());
        rejectMapper.insert(reject);
    }
}
