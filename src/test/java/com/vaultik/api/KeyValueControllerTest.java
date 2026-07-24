package com.vaultik.api;

import com.vaultik.core.KeyValueStore;
import com.vaultik.core.StoreStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.Serializable;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class KeyValueControllerTest {
    private MockMvc mvc;
    private FakeStore store;

    @BeforeEach
    void setUp() {
        store = new FakeStore();
        mvc = MockMvcBuilders
                .standaloneSetup(new KeyValueController(store))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void setReturnsCreated() throws Exception {
        mvc.perform(post("/set")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"key":"city","value":"Pune","ttlSeconds":60}
                                """))
                .andExpect(status().isCreated());

        assertThat(store.values).containsEntry("city", "Pune");
        assertThat(store.ttls).containsEntry("city", Duration.ofSeconds(60));
    }

    @Test
    void getReturnsValueWhenPresent() throws Exception {
        store.values.put("city", "Pune");

        mvc.perform(get("/get/city"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("city"))
                .andExpect(jsonPath("$.value").value("Pune"));
    }

    @Test
    void deleteReturnsNotFoundWhenMissing() throws Exception {
        mvc.perform(delete("/delete/missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    void statsReturnsCounters() throws Exception {
        mvc.perform(get("/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.capacity").value(10))
                .andExpect(jsonPath("$.hits").value(2));
    }

    @Test
    void healthReturnsUp() throws Exception {
        mvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    private static final class FakeStore implements KeyValueStore {
        private final Map<String, Serializable> values = new HashMap<>();
        private final Map<String, Duration> ttls = new HashMap<>();

        @Override
        public void set(String key, Serializable value) {
            values.put(key, value);
        }

        @Override
        public void set(String key, Serializable value, Duration ttl) {
            values.put(key, value);
            ttls.put(key, ttl);
        }

        @Override
        public Optional<Serializable> get(String key) {
            return Optional.ofNullable(values.get(key));
        }

        @Override
        public boolean delete(String key) {
            return values.remove(key) != null;
        }

        @Override
        public StoreStats stats() {
            return new StoreStats(1, 10, 2, 3, 4, 5, 6);
        }

        @Override
        public void close() {
        }
    }
}
