package com.ordercraft.orders.model;

import java.math.BigDecimal;

public record Payment(
    long id,
    BigDecimal amount,
    String reference,
    String recordedBy,
    String recordedAt
) {}
