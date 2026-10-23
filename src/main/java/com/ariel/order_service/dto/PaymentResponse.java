package com.ariel.order_service.dto;

public class PaymentResponse {

    private boolean approved;
    private String transactionId;

    public PaymentResponse(
            boolean approved,
            String transactionId) {

        this.approved = approved;
        this.transactionId = transactionId;
    }

    public boolean isApproved() {
        return approved;
    }

    public String getTransactionId() {
        return transactionId;
    }
}