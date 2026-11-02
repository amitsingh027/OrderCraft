package com.ordercraft;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(
    properties = {
        "spring.datasource.url=jdbc:h2:mem:orders;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "ordercraft.admin.password=TestPassword-Only123!",
    }
)
@AutoConfigureMockMvc
class SecurityPaymentTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate db;

    private MockHttpServletRequestBuilder postAs(String path, String role) {
        return post(path).with(user(role.toLowerCase()).roles(role)).with(csrf());
    }

    private long invoice() throws Exception {
        String result = mvc
            .perform(
                postAs("/api/orders", "SALES")
                    .contentType("application/json")
                    .content(
                        "{\"customer\":\"Payment Test\",\"product\":\"Bracket\",\"qty\":10,\"price\":100.01,\"due\":\"2026-12-01\"}"
                    )
            )
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
        long id = json.readTree(result).get("id").asLong();
        String base = "/api/orders/" + id;
        mvc.perform(postAs(base + "/bom", "PRODUCTION")).andExpect(status().isOk());
        mvc.perform(postAs(base + "/po", "PRODUCTION")).andExpect(status().isOk());
        for (int i = 0; i < 4; i++) mvc.perform(postAs(base + "/advance", "PRODUCTION")).andExpect(
            status().isOk()
        );
        mvc.perform(postAs(base + "/invoice", "ACCOUNTS")).andExpect(status().isOk());
        return id;
    }

    private ResultActions pay(long id, String key, String amount, String reference)
        throws Exception {
        return mvc.perform(
            postAs("/api/orders/" + id + "/payment", "ACCOUNTS")
                .header("Idempotency-Key", key)
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of("amount", new BigDecimal(amount), "reference", reference)
                    )
                )
        );
    }

    @Test
    void anonymousAndMissingCsrfAreRejected() throws Exception {
        mvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/activity")).andExpect(status().isUnauthorized());
        mvc.perform(
            post("/api/orders")
                .with(user("admin").roles("ADMIN"))
                .contentType("application/json")
                .content("{}")
        ).andExpect(status().isForbidden());
    }

    @Test
    void rolesAreEnforcedOnBackend() throws Exception {
        for (String role : List.of("SALES", "PRODUCTION", "ACCOUNTS")) {
            mvc.perform(get("/api/orders").with(user(role).roles(role))).andExpect(status().isOk());
            mvc.perform(get("/api/admin/users").with(user(role).roles(role))).andExpect(
                status().isForbidden()
            );
            for (String action : List.of("bom", "po", "advance", "invoice", "payment")) {
                boolean allowed = List.of("bom", "po", "advance").contains(action)
                    ? role.equals("PRODUCTION")
                    : role.equals("ACCOUNTS");
                if (!allowed) mvc.perform(postAs("/api/orders/999999/" + action, role)).andExpect(
                    status().isForbidden()
                );
            }
            if (!role.equals("SALES")) mvc.perform(
                postAs("/api/orders", role).contentType("application/json").content("{}")
            ).andExpect(status().isForbidden());
        }
    }

    @Test
    void actualLoginLogoutAndSessionCsrfWork() throws Exception {
        MvcResult tokenResult = mvc
            .perform(get("/api/auth/csrf"))
            .andExpect(status().isOk())
            .andReturn();
        var token = json.readTree(tokenResult.getResponse().getContentAsString());
        MockHttpSession session = (MockHttpSession) tokenResult.getRequest().getSession(false);
        mvc.perform(
            post("/api/auth/login")
                .session(session)
                .header(token.get("headerName").asText(), token.get("token").asText())
                .param("username", "admin")
                .param("password", "wrong-password")
        ).andExpect(status().isUnauthorized());
        mvc.perform(
            post("/api/auth/login")
                .session(session)
                .header(token.get("headerName").asText(), token.get("token").asText())
                .param("username", "admin")
                .param("password", "TestPassword-Only123!")
        ).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("ADMIN"));
        var fresh = mvc.perform(get("/api/auth/csrf").session(session)).andReturn();
        var csrf = json.readTree(fresh.getResponse().getContentAsString());
        mvc.perform(
            post("/api/auth/logout")
                .session(session)
                .header(csrf.get("headerName").asText(), csrf.get("token").asText())
        ).andExpect(status().isNoContent());
        assertTrue(session.isInvalid());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminsCreateHashedRoleAccounts() throws Exception {
        String name = "operator-" + UUID.randomUUID().toString().substring(0, 8);
        String body = json.writeValueAsString(
            Map.of("username", name, "password", "NewTeamPassword123!", "role", "PRODUCTION")
        );
        mvc.perform(
            postAs("/api/admin/users", "SALES").contentType("application/json").content(body)
        ).andExpect(status().isForbidden());
        mvc.perform(
            postAs("/api/admin/users", "ADMIN").contentType("application/json").content(body)
        )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.role").value("PRODUCTION"))
            .andExpect(jsonPath("$.password").doesNotExist());
        String hash = db.queryForObject(
            "SELECT password_hash FROM app_users WHERE username=?",
            String.class,
            name
        );
        assertNotEquals("NewTeamPassword123!", hash);
        assertTrue(hash.startsWith("$2"));
        mvc.perform(
            postAs("/api/admin/users", "ADMIN").contentType("application/json").content(body)
        ).andExpect(status().isConflict());
        mvc.perform(
            postAs("/api/admin/users", "ADMIN")
                .contentType("application/json")
                .content("{\"username\":\"bad-user\",\"password\":\"short\",\"role\":\"ADMIN\"}")
        ).andExpect(status().isBadRequest());
    }

    @Test
    void paymentReplayIsExactlyOnceAndConflictingReuseIsRejected() throws Exception {
        long id = invoice();
        String key = UUID.randomUUID().toString();
        pay(id, key, "400.05", "BANK-001")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paid").value(400.05));
        pay(id, key, "400.05", "bank-001")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paid").value(400.05));
        pay(id, key, "400.06", "BANK-001").andExpect(status().isConflict());
        pay(id, UUID.randomUUID().toString(), "400.05", "BANK-001").andExpect(
            status().isConflict()
        );
        pay(id, UUID.randomUUID().toString(), "600.06", "BANK-002").andExpect(
            status().isConflict()
        );
        pay(id, UUID.randomUUID().toString(), "600.05", "BANK-002")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paid").value(1000.10));
        pay(id, key, "400.05", "BANK-001")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paid").value(1000.10));
        assertEquals(
            2,
            db.queryForObject("SELECT COUNT(*) FROM payments WHERE order_id=?", Integer.class, id)
        );
        assertEquals(
            2,
            db.queryForObject(
                "SELECT COUNT(*) FROM audit_events WHERE order_id=? AND action='payment'",
                Integer.class,
                id
            )
        );
        mvc.perform(get("/api/orders/" + id + "/payments").with(user("accounts").roles("ACCOUNTS")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].reference").value("BANK-002"))
            .andExpect(jsonPath("$[0].recordedBy").value("accounts"));
    }

    @Test
    void invalidPaymentMetadataAndCrossOrderReplayAreRejected() throws Exception {
        long id = invoice();
        String key = UUID.randomUUID().toString();
        pay(id, "invalid", "1", "REF-001").andExpect(status().isConflict());
        pay(id, key, "1.001", "REF-001").andExpect(status().isConflict());
        pay(id, key, "1", "").andExpect(status().isConflict());
        pay(id, key, "1", "REF-001").andExpect(status().isOk());
        long other = invoice();
        pay(other, key, "1", "REF-001").andExpect(status().isConflict());
        assertEquals(
            0,
            db.queryForObject(
                "SELECT COUNT(*) FROM payments WHERE order_id=?",
                Integer.class,
                other
            )
        );
    }

    @Test
    void simultaneousPaymentRetriesCreateOneLedgerEntry() throws Exception {
        long id = invoice();
        String key = UUID.randomUUID().toString();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch gate = new CountDownLatch(1);
        try {
            Callable<Integer> task = () -> {
                gate.await();
                return pay(id, key, "200.01", "RETRY-001").andReturn().getResponse().getStatus();
            };
            Future<Integer> first = pool.submit(task),
                second = pool.submit(task);
            gate.countDown();
            assertEquals(200, first.get(15, TimeUnit.SECONDS));
            assertEquals(200, second.get(15, TimeUnit.SECONDS));
            assertEquals(
                1,
                db.queryForObject(
                    "SELECT COUNT(*) FROM payments WHERE order_id=?",
                    Integer.class,
                    id
                )
            );
            assertEquals(
                0,
                new BigDecimal("200.01").compareTo(
                    db.queryForObject(
                        "SELECT paid FROM customer_orders WHERE id=?",
                        BigDecimal.class,
                        id
                    )
                )
            );
        } finally {
            pool.shutdownNow();
        }
    }
}
