package com.ordercraft.orders.model;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateOrderRequest(
    @NotBlank @Size(max = 100) String customer,
    @NotBlank @Size(max = 100) String product,
    @Min(1) @Max(1000000) int qty,
    @NotNull
    @DecimalMin("1")
    @DecimalMax("100000000")
    @Digits(integer = 9, fraction = 2)
    BigDecimal price,
    @NotNull LocalDate due
) {}
