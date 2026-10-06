package com.evops.aqua.maint.service;

/**
 * 同一操作并发冲突：本请求未生效，已由他人生效执行（谁生效、谁被拒见消息与拒单审计表）。
 */
public class ConcurrentOperationRejectedException extends RuntimeException {

    private final String winnerOperator;
    private final String winnerRequestId;

    public ConcurrentOperationRejectedException(String message, String winnerOperator, String winnerRequestId) {
        super(message);
        this.winnerOperator = winnerOperator;
        this.winnerRequestId = winnerRequestId;
    }

    public String getWinnerOperator() { return winnerOperator; }
    public String getWinnerRequestId() { return winnerRequestId; }
}
