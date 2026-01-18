package com.example.barterchest.transaction;

import javax.annotation.Nonnull;

/**
 * Represents the result of a shop transaction attempt.
 */
public class TransactionResult {
    
    public enum Status {
        SUCCESS,
        INSUFFICIENT_FUNDS,
        INSUFFICIENT_STOCK,
        INSUFFICIENT_SPACE,
        INVENTORY_FULL,
        SHOP_NOT_CONFIGURED,
        SHOP_DOESNT_BUY,
        SHOP_DOESNT_SELL,
        INVALID_QUANTITY,
        TRANSACTION_ERROR
    }
    
    private final Status status;
    private final String message;
    private final int quantityTransacted;
    
    private TransactionResult(Status status, String message, int quantityTransacted) {
        this.status = status;
        this.message = message;
        this.quantityTransacted = quantityTransacted;
    }
    
    public static TransactionResult success(int quantity) {
        return new TransactionResult(Status.SUCCESS, "Transaction successful", quantity);
    }
    
    public static TransactionResult success(int quantity, String message) {
        return new TransactionResult(Status.SUCCESS, message, quantity);
    }
    
    public static TransactionResult failure(Status status, String message) {
        return new TransactionResult(status, message, 0);
    }
    
    @Nonnull
    public Status getStatus() {
        return status;
    }
    
    @Nonnull
    public String getMessage() {
        return message;
    }
    
    public int getQuantityTransacted() {
        return quantityTransacted;
    }
    
    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }
    
    public boolean isFailure() {
        return status != Status.SUCCESS;
    }
    
    @Override
    public String toString() {
        return "TransactionResult{status=" + status + ", message='" + message + "', quantity=" + quantityTransacted + '}';
    }
}
