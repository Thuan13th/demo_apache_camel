package com.demo_apache_camel.hub.config;

import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.support.processor.idempotent.MemoryIdempotentRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;

/**
 * [HUB IDEMPOTENCY REPOSITORY CONFIGURATION]
 * Cung cấp kho lưu trữ khóa giao dịch để chống xử lý trùng lặp (Idempotent Consumer EIP):
 * Sử dụng bộ nhớ LRU in-memory (sẵn sàng tráo đổi sang RedisIdempotentRepository trong môi trường Multi-Pod).
 */
@Configuration
public class IdempotencyConfig {

    @Bean("hubIdempotentRepository")
    public IdempotentRepository hubIdempotentRepository() {
        // Lưu trữ tối đa 10,000 transaction ID gần nhất để chặn trùng
        return new MemoryIdempotentRepository(new HashMap<>(10000));
    }
}
