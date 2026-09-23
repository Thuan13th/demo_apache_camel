package com.demo_apache_camel.hub.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * [HUB GLOBAL PROPERTIES - CONFIGURATION METADATA]
 * Nạp toàn bộ thông số cấu hình từ file application.yaml / biến môi trường:
 * Hỗ trợ quản lý động các thông số kết nối, rate limit, REST DSL, Storage và Partner Policy.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "hub")
public class HubProperties {

    private String name = "Enterprise Service Hub";
    private String environment = "local";
    private int defaultTimeoutMs = 5000;
    private boolean auditLogEnabled = true;

    private Rest rest = new Rest();
    private Storage storage = new Storage();
    private PartnerPolicy partnerPolicy = new PartnerPolicy();

    @Data
    public static class Rest {
        private String contextPath = "/api/v1";
        private String apiDocPath = "/api-doc";
        private boolean corsEnabled = true;
        private String title = "Enterprise Service Hub REST API";
        private String version = "v1";
    }

    @Data
    public static class Storage {
        private String inboundPath = "data/inbound";
        private String processedPath = "data/processed";
        private String samplesPath = "data/samples";
    }

    @Data
    public static class PartnerPolicy {
        private int maxRetryAttempts = 2;
        private int retryDelayMs = 1000;
        private int parallelTimeoutMs = 3000;
    }
}
