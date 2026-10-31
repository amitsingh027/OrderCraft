package com.ordercraft;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:orders;MODE=MySQL;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver","ordercraft.admin.password=TestPassword-Only123!"})
@AutoConfigureMockMvc
class WorkflowTest {
 private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authPost(String path){return post(path).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN")).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf());}
 @Autowired MockMvc mvc;
 @Autowired ObjectMapper json;
 @Test void orderToCashAndGuards() throws Exception {
  String created=mvc.perform(authPost("/api/orders").contentType("application/json").content("{\"customer\":\"Factory\",\"product\":\"Brackets\",\"qty\":10,\"price\":100,\"due\":\"2026-12-01\"}")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
  long id=json.readTree(created).get("id").asLong();String base="/api/orders/"+id;
  mvc.perform(authPost(base+"/invoice")).andExpect(status().isConflict());
  mvc.perform(authPost(base+"/po")).andExpect(status().isConflict());
  mvc.perform(authPost(base+"/bom")).andExpect(status().isOk()).andExpect(jsonPath("$.bom").value(true));
  mvc.perform(authPost(base+"/bom")).andExpect(status().isConflict());
  mvc.perform(authPost(base+"/po")).andExpect(status().isOk());
  for(int i=0;i<4;i++)mvc.perform(authPost(base+"/advance")).andExpect(status().isOk());
  mvc.perform(authPost(base+"/advance")).andExpect(status().isConflict());
  mvc.perform(authPost(base+"/invoice")).andExpect(status().isOk()).andExpect(jsonPath("$.invoiced").value(true));
  mvc.perform(authPost(base+"/invoice")).andExpect(status().isConflict());
  mvc.perform(authPost(base+"/payment").header("Idempotency-Key",java.util.UUID.randomUUID().toString()).contentType("application/json").content("{\"amount\":-1}")).andExpect(status().isConflict());
  mvc.perform(authPost(base+"/payment").header("Idempotency-Key",java.util.UUID.randomUUID().toString()).contentType("application/json").content("{\"amount\":400,\"reference\":\"FIRST-400\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.paid").value(400));
  mvc.perform(authPost(base+"/payment").header("Idempotency-Key",java.util.UUID.randomUUID().toString()).contentType("application/json").content("{\"amount\":601,\"reference\":\"OVER-601\"}")).andExpect(status().isConflict());
  mvc.perform(authPost(base+"/payment").header("Idempotency-Key",java.util.UUID.randomUUID().toString()).contentType("application/json").content("{\"amount\":600,\"reference\":\"FINAL-600\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.paid").value(1000));
  mvc.perform(get(base+"/documents").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN"))).andExpect(jsonPath("$.bom.length()").value(2)).andExpect(jsonPath("$.payments.length()").value(2));
 }
 @Test void invalidOrderRejected() throws Exception {mvc.perform(authPost("/api/orders").contentType("application/json").content("{\"customer\":\"\",\"product\":\"Brackets\",\"qty\":0,\"price\":-1,\"due\":\"2026-12-01\"}")).andExpect(status().isBadRequest());}
}

