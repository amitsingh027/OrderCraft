package com.ordercraft.orders;

import com.ordercraft.orders.model.*;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** HTTP entry points. Workflow decisions belong to OrderService. */
@RestController
@RequestMapping("/api")
public class OrderController {

    private final OrderService orders;

    public OrderController(OrderService orders) {
        this.orders = orders;
    }

    @GetMapping("/orders")
    public List<Order> list() {
        return orders.list();
    }

    @GetMapping("/orders/{id}")
    public Order detail(@PathVariable long id) {
        return orders.detail(id);
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public Order create(@Valid @RequestBody CreateOrderRequest request) {
        return orders.create(request);
    }

    @PostMapping("/orders/{id}/{action}")
    public Order action(
        @PathVariable long id,
        @PathVariable String action,
        @RequestBody(required = false) OrderActionRequest request,
        @RequestHeader(value = "Idempotency-Key", required = false) String requestKey
    ) {
        return orders.action(id, action, request, requestKey);
    }

    @GetMapping("/orders/{id}/documents")
    public Map<String, Object> documents(@PathVariable long id) {
        return orders.documents(id);
    }

    @GetMapping("/orders/{id}/payments")
    public List<Payment> payments(@PathVariable long id) {
        return orders.payments(id);
    }

    @GetMapping("/activity")
    public List<AuditEvent> activity() {
        return orders.activity();
    }
}
