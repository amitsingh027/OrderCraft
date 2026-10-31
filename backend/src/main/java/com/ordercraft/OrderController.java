package com.ordercraft;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.sql.Statement;
import org.springframework.security.core.context.SecurityContextHolder;

@RestController @RequestMapping("/api")
public class OrderController {
 private final JdbcTemplate db;
 public OrderController(JdbcTemplate db){this.db=db;}
 public record Order(long id,String customer,String product,int qty,BigDecimal price,LocalDate due,String status,BigDecimal paid,boolean bom,boolean po,boolean invoiced){}
 public record NewOrder(@NotBlank @Size(max=100) String customer,@NotBlank @Size(max=100) String product,@Min(1) @Max(1000000) int qty,@NotNull @DecimalMin("1") @DecimalMax("100000000") @Digits(integer=9,fraction=2) BigDecimal price,@NotNull LocalDate due){}
 public record Action(BigDecimal amount, String reference){}
 public record Payment(long id, BigDecimal amount, String reference, String recordedBy, String recordedAt){}
 public record AuditEvent(long id,long orderId,String action,String actor,String recordedAt){}
 private final RowMapper<Order> mapper=(r,n)->new Order(r.getLong("id"),r.getString("customer"),r.getString("product"),r.getInt("qty"),r.getBigDecimal("price"),r.getDate("due").toLocalDate(),r.getString("status"),r.getBigDecimal("paid"),r.getBoolean("bom"),r.getBoolean("po"),r.getBoolean("invoiced"));
 @GetMapping("/orders") public List<Order> list(){return db.query("SELECT * FROM customer_orders ORDER BY id DESC",mapper);}
 private Order get(long id,boolean lock){return db.query("SELECT * FROM customer_orders WHERE id=?"+(lock?" FOR UPDATE":""),mapper,id).stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Order not found"));}
 @GetMapping("/orders/{id}") public Order detail(@PathVariable long id){return get(id,false);}
 @PostMapping("/orders") @ResponseStatus(HttpStatus.CREATED) @Transactional public Order create(@Valid @RequestBody NewOrder body){
  var key=new GeneratedKeyHolder();
  db.update(c->{var s=c.prepareStatement("INSERT INTO customer_orders(customer,product,qty,price,due) VALUES(?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS);s.setString(1,body.customer().trim());s.setString(2,body.product());s.setInt(3,body.qty());s.setBigDecimal(4,body.price());s.setObject(5,body.due());return s;},key);
  long id=Objects.requireNonNull(key.getKey()).longValue();audit(id,"created");return get(id,false);
 }
 private void require(boolean condition,String message){if(!condition)throw new ResponseStatusException(HttpStatus.CONFLICT,message);}
 private String actor(){return SecurityContextHolder.getContext().getAuthentication().getName();}
 private void audit(long id,String action){db.update("INSERT INTO audit_events(order_id,action,actor) VALUES(?,?,?)",id,action,actor());}
 @PostMapping("/orders/{id}/{action}") @Transactional public Order action(@PathVariable long id,@PathVariable String action,@RequestBody(required=false) Action body,@RequestHeader(value="Idempotency-Key",required=false) String requestKey){
  Order o=get(id,true);BigDecimal total=o.price().multiply(BigDecimal.valueOf(o.qty()));
  switch(action){
   case "bom" -> {require(!o.bom(),"BOM already generated");db.update("INSERT INTO bom_lines(order_id,material,quantity,unit) VALUES(?,?,?,?)",id,"Steel",o.qty()*2,"kg");db.update("INSERT INTO bom_lines(order_id,material,quantity,unit) VALUES(?,?,?,?)",id,"Fasteners",o.qty()*4,"pcs");db.update("UPDATE customer_orders SET bom=TRUE WHERE id=?",id);}
   case "po" -> {require(o.bom()&&!o.po(),"Generate BOM first; only one purchase order is allowed");db.update("INSERT INTO purchase_orders(order_id,amount) VALUES(?,?)",id,BigDecimal.valueOf(o.qty()).multiply(BigDecimal.valueOf(250)));db.update("UPDATE customer_orders SET po=TRUE WHERE id=?",id);}
   case "advance" -> {require(o.po(),"Create the purchase order first");var stages=List.of("Confirmed","Materials ready","In production","Ready to ship","Delivered");int i=stages.indexOf(o.status());require(i>=0&&i<4,"Delivered orders cannot advance");db.update("UPDATE customer_orders SET status=? WHERE id=?",stages.get(i+1),id);}
   case "invoice" -> {require(o.status().equals("Delivered")&&!o.invoiced(),"Deliver the order first; only one invoice is allowed");db.update("INSERT INTO invoices(order_id,amount) VALUES(?,?)",id,total);db.update("UPDATE customer_orders SET invoiced=TRUE WHERE id=?",id);}
   case "payment" -> {
    require(o.invoiced(),"Create an invoice first");
    BigDecimal amount=body==null?null:body.amount();
    String reference=body==null||body.reference()==null?"":body.reference().trim().toUpperCase(Locale.ROOT);
    require(requestKey!=null&&requestKey.matches("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}"),"A valid Idempotency-Key UUID is required.");
    requestKey=requestKey.toLowerCase(Locale.ROOT);
    require(amount!=null&&amount.signum()>0&&amount.scale()<=2,"Payment must be positive with at most two decimals.");
    require(reference.matches("[A-Z0-9][A-Z0-9._/-]{2,99}"),"Use a 3–100 character receipt/reference containing letters, numbers, dots, underscores, slashes or hyphens.");
    var previous=db.query("SELECT order_id,amount,reference FROM payments WHERE request_key=?",
      (rs,n)->new SavedPayment(rs.getLong("order_id"),rs.getBigDecimal("amount"),rs.getString("reference")),requestKey);
    if(!previous.isEmpty()){
     SavedPayment saved=previous.get(0);
     require(saved.orderId()==id&&saved.amount().compareTo(amount)==0&&saved.reference().equals(reference),"This request ID was already used with different payment details.");
     return get(id,false);
    }
    require(amount.compareTo(total.subtract(o.paid()))<=0,"Payment must not exceed the outstanding balance.");
    require(db.queryForObject("SELECT COUNT(*) FROM payments WHERE order_id=? AND reference=?",Integer.class,id,reference)==0,"This payment reference is already recorded for this order.");
    db.update("INSERT INTO payments(order_id,amount,request_key,reference,recorded_by) VALUES(?,?,?,?,?)",id,amount,requestKey,reference,actor());
    db.update("UPDATE customer_orders SET paid=paid+? WHERE id=?",amount,id);
   }
   default -> throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Unknown action");
  }
  audit(id,action);return get(id,false);
 }
 @GetMapping("/orders/{id}/documents") public Map<String,Object> documents(@PathVariable long id){get(id,false);return Map.of("bom",db.queryForList("SELECT * FROM bom_lines WHERE order_id=?",id),"purchaseOrders",db.queryForList("SELECT * FROM purchase_orders WHERE order_id=?",id),"invoices",db.queryForList("SELECT * FROM invoices WHERE order_id=?",id),"payments",db.queryForList("SELECT * FROM payments WHERE order_id=?",id));}
 private record SavedPayment(long orderId,BigDecimal amount,String reference){}
 @GetMapping("/orders/{id}/payments") public List<Payment> payments(@PathVariable long id){
  get(id,false);
  return db.query("SELECT * FROM payments WHERE order_id=? ORDER BY id DESC",
   (rs,n)->new Payment(rs.getLong("id"),rs.getBigDecimal("amount"),rs.getString("reference"),rs.getString("recorded_by"),rs.getTimestamp("recorded_at").toInstant().toString()),id);
 }
 @GetMapping("/activity") public List<AuditEvent> activity(){
  return db.query("SELECT * FROM audit_events ORDER BY id DESC LIMIT 100",
   (rs,n)->new AuditEvent(rs.getLong("id"),rs.getLong("order_id"),rs.getString("action"),rs.getString("actor"),rs.getTimestamp("recorded_at").toInstant().toString()));
 }
}
