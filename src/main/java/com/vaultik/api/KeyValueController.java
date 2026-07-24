package com.vaultik.api;

import com.vaultik.core.KeyValueStore;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
public class KeyValueController {
    private final KeyValueStore store;

    public KeyValueController(KeyValueStore store) {
        this.store = store;
    }

    @PostMapping("/set")
    public ResponseEntity<Void> set(@Valid @RequestBody SetRequest request) {
        if (request.ttlSeconds() == null) {
            store.set(request.key(), request.value());
        } else {
            store.set(request.key(), request.value(), Duration.ofSeconds(request.ttlSeconds()));
        }
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/get/{key}")
    public ResponseEntity<ValueResponse> get(@PathVariable String key) {
        return store.get(key)
                .map(value -> ResponseEntity.ok(new ValueResponse(key, value)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/delete/{key}")
    public ResponseEntity<Void> delete(@PathVariable String key) {
        boolean removed = store.delete(key);
        return removed ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @GetMapping("/stats")
    public StatsResponse stats() {
        return StatsResponse.from(store.stats());
    }

    @GetMapping("/health")
    public HealthResponse health() {
        return new HealthResponse("UP");
    }
}
