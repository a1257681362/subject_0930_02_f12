package com.evops.aqua.maint.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.maint.entity.MaintConcurrencyReject;
import com.evops.aqua.maint.mapper.MaintConcurrencyRejectMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Supplier;

/**
 * 同一操作并发仲裁：生效者先抢占生效占位（独立事务提交后对他事务可见），业务事务提交后释放；
 * 并发后来者抢位失败 → 独立事务写拒单审计（业务回滚不影响留痕）→ 抛冲突异常，明确谁生效谁被拒。
 * 占位的抢占/释放/拒单写库全部委托 {@link MaintClaimTxService}（REQUIRES_NEW，经 Spring 代理生效）。
 */
@Service
public class MaintConcurrencyService {

    public static final String OP_WINDOW_OPEN = "WINDOW_OPEN";
    public static final String OP_PLAN_CREATE = "PLAN_CREATE";
    public static final String OP_PLAN_ADJUST = "PLAN_ADJUST";
    public static final String OP_PLAN_CANCEL = "PLAN_CANCEL";
    public static final String OP_ORDER_GENERATE = "ORDER_GENERATE";
    public static final String OP_ORDER_LIFECYCLE = "ORDER_LIFECYCLE";

    public static final String TARGET_DEVICE = "DEVICE";
    public static final String TARGET_PLAN_TEMPLATE = "DEVICE_TEMPLATE";
    public static final String TARGET_ORDER = "WORK_ORDER";

    private final MaintClaimTxService claimTxService;
    private final MaintConcurrencyRejectMapper rejectMapper;
    private final MaintOperationHook hook;

    public MaintConcurrencyService(MaintClaimTxService claimTxService,
                                   MaintConcurrencyRejectMapper rejectMapper,
                                   MaintOperationHook hook) {
        this.claimTxService = claimTxService;
        this.rejectMapper = rejectMapper;
        this.hook = hook;
    }

    /**
     * 以独占方式执行操作。调用方门面不得自带事务：先抢位（独立事务提交），再执行业务事务，最后释放。
     * 并发冲突时写拒单审计并抛 {@link ConcurrentOperationRejectedException}。
     */
    public <T> T runExclusive(String operation, String targetType, String targetId,
                              String operator, String requestId, String actionDesc,
                              Supplier<T> action) {
        ClaimAcquire acquired = claimTxService.tryAcquire(
                operation, targetType, targetId, operator, requestId);
        if (!acquired.acquired) {
            claimTxService.recordReject(operation, targetType, targetId, operator, requestId,
                    acquired.holderOperator, acquired.holderRequestId, actionDesc);
            throw new ConcurrentOperationRejectedException(
                    "操作冲突：相同操作正由[" + acquired.holderOperator + "]执行（请求号="
                            + acquired.holderRequestId + "），本次请求[" + operator
                            + "]被拒绝，不生效；生效者=" + acquired.holderOperator,
                    acquired.holderOperator, acquired.holderRequestId);
        }
        try {
            hook.onClaimAcquired(operation, targetType, targetId);
            return action.get();
        } finally {
            claimTxService.release(acquired.claimId);
        }
    }

    public List<MaintConcurrencyReject> listRejects(String targetType, String targetId) {
        return rejectMapper.selectList(new QueryWrapper<MaintConcurrencyReject>()
                .eq(targetType != null && !targetType.trim().isEmpty(), "target_type", targetType)
                .eq(targetId != null && !targetId.trim().isEmpty(), "target_id", targetId)
                .orderByAsc("id"));
    }

    public List<MaintConcurrencyReject> listAllRejects() {
        return rejectMapper.selectList(new QueryWrapper<MaintConcurrencyReject>().orderByAsc("id"));
    }

    /** 占位获取结果。 */
    public static class ClaimAcquire {
        boolean acquired;
        Long claimId;
        String holderOperator;
        String holderRequestId;

        static ClaimAcquire acquired(Long claimId, String operator, String requestId) {
            ClaimAcquire r = new ClaimAcquire();
            r.acquired = true;
            r.claimId = claimId;
            r.holderOperator = operator;
            r.holderRequestId = requestId;
            return r;
        }

        static ClaimAcquire rejected(String winnerOperator, String winnerRequestId) {
            ClaimAcquire r = new ClaimAcquire();
            r.acquired = false;
            r.holderOperator = winnerOperator;
            r.holderRequestId = winnerRequestId;
            return r;
        }
    }
}
