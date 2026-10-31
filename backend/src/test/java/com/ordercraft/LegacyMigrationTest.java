package com.ordercraft;

import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LegacyMigrationTest {
 @Test void existingOrdersAndPaymentsSurviveUpgrade() throws Exception {
  String url="jdbc:h2:mem:legacy;MODE=MySQL;DB_CLOSE_DELAY=-1";
  Flyway.configure().dataSource(url,"sa","").target("1").load().migrate();
  try(var c=DriverManager.getConnection(url,"sa","");var s=c.createStatement()){
   s.execute("INSERT INTO customer_orders(customer,product,qty,price,due,status,paid,bom,po,invoiced) VALUES('Legacy','Bracket',10,100,'2026-12-01','Delivered',50,TRUE,TRUE,TRUE)");
   s.execute("INSERT INTO payments(order_id,amount) VALUES(1,50)");
   s.execute("DROP TABLE \"flyway_schema_history\"");
  }
  Flyway.configure().dataSource(url,"sa","").baselineOnMigrate(true).baselineVersion("1").load().migrate();
  try(var c=DriverManager.getConnection(url,"sa","");var s=c.createStatement()){
   var r=s.executeQuery("SELECT paid FROM customer_orders WHERE id=1");assertTrue(r.next());assertEquals(50,r.getInt(1));
   r=s.executeQuery("SELECT amount,request_key,reference FROM payments WHERE order_id=1");assertTrue(r.next());
   assertEquals(50,r.getInt(1));assertNull(r.getString(2));assertNull(r.getString(3));
   r=s.executeQuery("SELECT COUNT(*) FROM app_users");assertTrue(r.next());assertEquals(0,r.getInt(1));
  }
 }
}
