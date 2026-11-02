package com.ordercraft.orders.model;

import java.math.BigDecimal;

public record OrderActionRequest(BigDecimal amount, String reference) {}
