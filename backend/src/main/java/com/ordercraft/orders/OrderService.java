package com.ordercraft.orders;

import com.ordercraft.orders.model.*;
import java.math.BigDecimal;
import java.sql.Statement;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Business rules and transactional database updates for the order workflow. */
@Service
public class OrderService {

    private final JdbcTemplate db;

    public OrderService(JdbcTemplate db) {
        this.db = db;
    }

    private final RowMapper<Order> orderMapper = (resultSet, rowNumber) ->
        new Order(
            resultSet.getLong("id"),
            resultSet.getString("customer"),
            resultSet.getString("product"),
            resultSet.getInt("qty"),
            resultSet.getBigDecimal("price"),
            resultSet.getDate("due").toLocalDate(),
            resultSet.getString("status"),
            resultSet.getBigDecimal("paid"),
            resultSet.getBoolean("bom"),
            resultSet.getBoolean("po"),
            resultSet.getBoolean("invoiced")
        );

    public List<Order> list() {
        return db.query("SELECT * FROM customer_orders ORDER BY id DESC", orderMapper);
    }

    // A write must lock the order until its transaction finishes.
    private Order get(long id, boolean lock) {
        return db
            .query(
                "SELECT * FROM customer_orders WHERE id=?" + (lock ? " FOR UPDATE" : ""),
                orderMapper,
                id
            )
            .stream()
            .findFirst()
            .orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found")
            );
    }

    public Order detail(long id) {
        return get(id, false);
    }

    @Transactional
    public Order create(CreateOrderRequest body) {
        var generatedKey = new GeneratedKeyHolder();
        db.update(connection -> {
            var statement = connection.prepareStatement(
                "INSERT INTO customer_orders(customer,product,qty,price,due) VALUES(?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setString(1, body.customer().trim());
            statement.setString(2, body.product());
            statement.setInt(3, body.qty());
            statement.setBigDecimal(4, body.price());
            statement.setObject(5, body.due());
            return statement;
        }, generatedKey);
        long id = Objects.requireNonNull(generatedKey.getKey()).longValue();
        audit(id, "created");
        return get(id, false);
    }

    private void require(boolean condition, String message) {
        if (!condition) throw new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private String actor() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    private void audit(long id, String action) {
        db.update(
            "INSERT INTO audit_events(order_id,action,actor) VALUES(?,?,?)",
            id,
            action,
            actor()
        );
    }

    /** Locks one order and applies a single allowed workflow action atomically. */
    @Transactional
    public Order action(long id, String action, OrderActionRequest body, String requestKey) {
        Order order = get(id, true);
        BigDecimal total = order.price().multiply(BigDecimal.valueOf(order.qty()));
        switch (action) {
            case "bom" -> generateBom(id, order);
            case "po" -> createPurchaseOrder(id, order);
            case "advance" -> advanceStatus(id, order);
            case "invoice" -> createInvoice(id, order, total);
            case "payment" -> {
                // A replay returns the saved result without a second receipt or audit event.
                if (!recordPayment(id, order, total, body, requestKey)) return get(id, false);
            }
            default -> throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown action");
        }
        audit(id, action);
        return get(id, false);
    }

    // The fixed recipe and PO estimate are demonstration rules, not inventory calculations.
    private void generateBom(long id, Order order) {
        require(!order.bom(), "BOM already generated");
        db.update(
            "INSERT INTO bom_lines(order_id,material,quantity,unit) VALUES(?,?,?,?)",
            id,
            "Steel",
            order.qty() * 2,
            "kg"
        );
        db.update(
            "INSERT INTO bom_lines(order_id,material,quantity,unit) VALUES(?,?,?,?)",
            id,
            "Fasteners",
            order.qty() * 4,
            "pcs"
        );
        db.update("UPDATE customer_orders SET bom=TRUE WHERE id=?", id);
    }

    private void createPurchaseOrder(long id, Order order) {
        require(
            order.bom() && !order.po(),
            "Generate BOM first; only one purchase order is allowed"
        );
        db.update(
            "INSERT INTO purchase_orders(order_id,amount) VALUES(?,?)",
            id,
            BigDecimal.valueOf(order.qty()).multiply(BigDecimal.valueOf(250))
        );
        db.update("UPDATE customer_orders SET po=TRUE WHERE id=?", id);
    }

    private void advanceStatus(long id, Order order) {
        require(order.po(), "Create the purchase order first");
        var stages = List.of(
            "Confirmed",
            "Materials ready",
            "In production",
            "Ready to ship",
            "Delivered"
        );
        int i = stages.indexOf(order.status());
        require(i >= 0 && i < 4, "Delivered orders cannot advance");
        db.update("UPDATE customer_orders SET status=? WHERE id=?", stages.get(i + 1), id);
    }

    private void createInvoice(long id, Order order, BigDecimal total) {
        require(
            order.status().equals("Delivered") && !order.invoiced(),
            "Deliver the order first; only one invoice is allowed"
        );
        db.update("INSERT INTO invoices(order_id,amount) VALUES(?,?)", id, total);
        db.update("UPDATE customer_orders SET invoiced=TRUE WHERE id=?", id);
    }

    private boolean recordPayment(
        long id,
        Order order,
        BigDecimal total,
        OrderActionRequest body,
        String requestKey
    ) {
        require(order.invoiced(), "Create an invoice first");
        BigDecimal amount = body == null ? null : body.amount();
        String reference =
            body == null || body.reference() == null
                ? ""
                : body.reference().trim().toUpperCase(Locale.ROOT);
        require(
            requestKey != null &&
                requestKey.matches(
                    "[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}"
                ),
            "A valid Idempotency-Key UUID is required."
        );
        requestKey = requestKey.toLowerCase(Locale.ROOT);
        require(
            amount != null && amount.signum() > 0 && amount.scale() <= 2,
            "Payment must be positive with at most two decimals."
        );
        require(
            reference.matches("[A-Z0-9][A-Z0-9._/-]{2,99}"),
            "Use a 3–100 character receipt/reference containing letters, numbers, dots, underscores, slashes or hyphens."
        );
        // A retry is checked before the balance because the first request may already have succeeded.
        var previous = db.query(
            "SELECT order_id,amount,reference FROM payments WHERE request_key=?",
            (rs, n) ->
                new SavedPayment(
                    rs.getLong("order_id"),
                    rs.getBigDecimal("amount"),
                    rs.getString("reference")
                ),
            requestKey
        );
        if (!previous.isEmpty()) {
            SavedPayment saved = previous.get(0);
            require(
                saved.orderId() == id &&
                    saved.amount().compareTo(amount) == 0 &&
                    saved.reference().equals(reference),
                "This request ID was already used with different payment details."
            );
            return false;
        }
        require(
            amount.compareTo(total.subtract(order.paid())) <= 0,
            "Payment must not exceed the outstanding balance."
        );
        require(
            db.queryForObject(
                "SELECT COUNT(*) FROM payments WHERE order_id=? AND reference=?",
                Integer.class,
                id,
                reference
            ) == 0,
            "This payment reference is already recorded for this order."
        );
        db.update(
            "INSERT INTO payments(order_id,amount,request_key,reference,recorded_by) VALUES(?,?,?,?,?)",
            id,
            amount,
            requestKey,
            reference,
            actor()
        );
        db.update("UPDATE customer_orders SET paid=paid+? WHERE id=?", amount, id);

        return true;
    }

    public Map<String, Object> documents(long id) {
        get(id, false);
        return Map.of(
            "bom",
            db.queryForList("SELECT * FROM bom_lines WHERE order_id=?", id),
            "purchaseOrders",
            db.queryForList("SELECT * FROM purchase_orders WHERE order_id=?", id),
            "invoices",
            db.queryForList("SELECT * FROM invoices WHERE order_id=?", id),
            "payments",
            db.queryForList("SELECT * FROM payments WHERE order_id=?", id)
        );
    }

    private record SavedPayment(long orderId, BigDecimal amount, String reference) {}

    public List<Payment> payments(long id) {
        get(id, false);
        return db.query(
            "SELECT * FROM payments WHERE order_id=? ORDER BY id DESC",
            (rs, n) ->
                new Payment(
                    rs.getLong("id"),
                    rs.getBigDecimal("amount"),
                    rs.getString("reference"),
                    rs.getString("recorded_by"),
                    rs.getTimestamp("recorded_at").toInstant().toString()
                ),
            id
        );
    }

    public List<AuditEvent> activity() {
        return db.query("SELECT * FROM audit_events ORDER BY id DESC LIMIT 100", (rs, n) ->
            new AuditEvent(
                rs.getLong("id"),
                rs.getLong("order_id"),
                rs.getString("action"),
                rs.getString("actor"),
                rs.getTimestamp("recorded_at").toInstant().toString()
            )
        );
    }
}
