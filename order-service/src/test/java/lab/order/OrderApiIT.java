package lab.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import lab.order.application.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.hikari.maximum-pool-size=1") @AutoConfigureMockMvc
class OrderApiIT {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean InventoryGateway inventory;
    final String body = "{\"lines\":[{\"sku\":\"BOOK\",\"quantity\":2,\"unitPrice\":12.50,\"currency\":\"EUR\"}]}";
    @BeforeEach void clean() { jdbc.update("DELETE FROM order_lines"); jdbc.update("DELETE FROM orders"); reset(inventory); }
    @Test void placesAndReadsPersistedOrder() throws Exception {
        var response = mvc.perform(post("/api/orders").contentType("application/json").content(body)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PLACED")).andExpect(jsonPath("$.total").value(25.0)).andReturn().getResponse();
        String id = mapper.readTree(response.getContentAsString()).path("id").asText();
        assertEquals("/api/orders/" + id, response.getHeader("Location"));
        mvc.perform(get("/api/orders/" + id)).andExpect(status().isOk()).andExpect(jsonPath("$.lines[0].quantity").value(2)).andExpect(jsonPath("$.total").value(25.0));
        verify(inventory).reserve(any(), any());
    }
    @Test void rejectsAndReadsInsufficientStockOrder() throws Exception {
        doThrow(new StockUnavailable("No stock")).when(inventory).reserve(any(), any());
        var response = mvc.perform(post("/api/orders").contentType("application/json").content(body)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("REJECTED")).andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK")).andReturn().getResponse();
        mvc.perform(get(response.getHeader("Location"))).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
    }
    @Test void inventoryFailureReturnsClear503() throws Exception {
        doThrow(new InventoryUnavailable(new RuntimeException("Timeout"))).when(inventory).reserve(any(), any());
        mvc.perform(post("/api/orders").contentType("application/json").content(body)).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("INVENTORY_UNAVAILABLE")).andExpect(jsonPath("$.message").isNotEmpty());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class));
    }
    @Test void missingOrderIs404() throws Exception { mvc.perform(get("/api/orders/" + UUID.randomUUID())).andExpect(status().isNotFound()); }
    @Test void invalidQuantityIs400() throws Exception {
        mvc.perform(post("/api/orders").contentType("application/json").content(body.replace("\"quantity\":2", "\"quantity\":0")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST")); verifyNoInteractions(inventory);
    }
    @Test void invalidCurrencyIs400() throws Exception {
        mvc.perform(post("/api/orders").contentType("application/json").content(body.replace("EUR", "ZZZ"))).andExpect(status().isBadRequest()); verifyNoInteractions(inventory);
    }
    @Test void emptyLinesAre400() throws Exception { mvc.perform(post("/api/orders").contentType("application/json").content("{\"lines\":[]}")).andExpect(status().isBadRequest()); }
    @Test void malformedJsonIs400() throws Exception { mvc.perform(post("/api/orders").contentType("application/json").content("{" )).andExpect(status().isBadRequest()); }
    @Test void duplicateSkuIs400() throws Exception {
        var line = mapper.readTree(body).path("lines").get(0);
        mvc.perform(post("/api/orders").contentType("application/json").content("{\"lines\":[" + line + "," + line + "]}"))
                .andExpect(status().isBadRequest()); verifyNoInteractions(inventory);
    }
    @Test void exposesHealthAndOpenApi() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andExpect(jsonPath("$.paths['/api/orders'].post").exists());
    }
}
