package com.ordercraft.orders.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record Order(
    long id,
    String customer,
    String product,
    int qty,
    BigDecimal price,
    LocalDate due,
    String status,
    BigDecimal paid,
    boolean bom,
    boolean po,
    boolean invoiced
) {}
