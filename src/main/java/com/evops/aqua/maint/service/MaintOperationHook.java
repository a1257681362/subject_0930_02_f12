package com.evops.aqua.maint.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.CountDownLatch;

/**
 * 操作执行挂点：默认空实现。测试可武装门闩，在"生效请求已抢占占位、业务事务执行前"暂停，
 * 以确定性制造并发：生效者持占位期间发起竞争请求，验证"只一生效、余者拒绝并审计"。
 */
@Component
public class MaintOperationHook {

    private volatile CountDownLatch acquiredSignal;
    private volatile CountDownLatch permit;

    /** 武装：下一次抢到占位后阻塞，直到 {@link #release()}。 */
    public synchronized void arm() {
        this.acquiredSignal = new CountDownLatch(1);
        this.permit = new CountDownLatch(1);
    }

    /** 等待生效者抢到占位。 */
    public void awaitAcquired() throws InterruptedException {
        CountDownLatch signal = this.acquiredSignal;
        if (signal != null) {
            signal.await();
        }
    }

    /** 放行被阻塞的生效者。 */
    public void release() {
        CountDownLatch latch = this.permit;
        if (latch != null) {
            latch.countDown();
        }
    }

    public synchronized void reset() {
        this.acquiredSignal = null;
        this.permit = null;
    }

    public void onClaimAcquired(String operation, String targetType, String targetId) {
        CountDownLatch signal = this.acquiredSignal;
        CountDownLatch gate = this.permit;
        if (signal != null) {
            signal.countDown();
        }
        if (gate != null) {
            try {
                gate.await();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
