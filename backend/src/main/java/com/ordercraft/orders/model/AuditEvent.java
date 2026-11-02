package com.ordercraft.orders.model;

public record AuditEvent(long id, long orderId, String action, String actor, String recordedAt) {}
