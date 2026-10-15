package com.fraudshield.txn.payment;

import com.fraudshield.contracts.Channel;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record PaymentRequest(
        @NotBlank @Pattern(regexp = "^[a-zA-Z0-9.\\-_]{2,64}@[a-zA-Z]{2,32}$", message = "must be a valid UPI ID")
        String payerVpa,
        @NotBlank @Pattern(regexp = "^[a-zA-Z0-9.\\-_]{2,64}@[a-zA-Z]{2,32}$", message = "must be a valid UPI ID")
        String payeeVpa,
        @NotNull @DecimalMin("1.00") @DecimalMax(value = "500000.00", message = "exceeds the UPI per-transaction limit")
        BigDecimal amount,
        Channel channel,
        @NotBlank @Size(max = 64) String deviceId,
        Double lat,
        Double lon,
        @Size(max = 140) String note) {
}
