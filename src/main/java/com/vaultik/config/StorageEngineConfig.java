package com.vaultik.config;

import com.vaultik.core.KeyValueStore;
import com.vaultik.core.StoreConfiguration;
import com.vaultik.eviction.EvictionPolicyFactory;
import com.vaultik.persistence.FileSnapshotStore;
import com.vaultik.persistence.FileWriteAheadLog;
import com.vaultik.storage.PersistentKeyValueStore;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Path;

@Configuration
@EnableConfigurationProperties(VaultikProperties.class)
public class StorageEngineConfig {
    @Bean
    KeyValueStore keyValueStore(VaultikProperties properties) throws IOException {
        StoreConfiguration configuration = new StoreConfiguration(
                properties.getCapacity(),
                Path.of(properties.getWalPath()),
                Path.of(properties.getSnapshotPath()),
                properties.getSnapshotInterval()
        );
        return new PersistentKeyValueStore(
                configuration,
                EvictionPolicyFactory.create(properties.getEvictionPolicy()),
                new FileWriteAheadLog(configuration.walPath()),
                new FileSnapshotStore(configuration.snapshotPath())
        );
    }
}
