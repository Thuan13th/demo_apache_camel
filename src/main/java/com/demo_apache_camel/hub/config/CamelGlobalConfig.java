package com.demo_apache_camel.hub.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.ThreadPoolProfileBuilder;
import org.apache.camel.spring.boot.CamelContextConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * [CAMEL GLOBAL CONFIGURATION - CHUẨN DOANH NGHIỆP]
 * Cấu hình toàn cục cho động cơ Apache Camel:
 * 1. Khởi tạo ThreadPoolProfile chuyên dụng cho xử lý song song (Scatter-Gather).
 * 2. Thiết lập chính sách kiểm soát luồng và giám sát vòng đời CamelContext.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class CamelGlobalConfig {

    private final HubProperties hubProperties;

    @Bean
    public CamelContextConfiguration contextConfiguration() {
        return new CamelContextConfiguration() {
            @Override
            public void beforeApplicationStart(CamelContext camelContext) {
                log.info("[CAMEL-INIT] Đang khởi tạo CamelContext '{}' cho môi trường '{}'...",
                        hubProperties.getName(), hubProperties.getEnvironment());

                // 1. Tạo Thread Pool Profile chuyên biệt cho xử lý song song đa hãng (Scatter-Gather EIP)
                ThreadPoolProfileBuilder poolProfile = new ThreadPoolProfileBuilder("customHubParallelPool");
                poolProfile.poolSize(10)
                           .maxPoolSize(50)
                           .maxQueueSize(1000);
                camelContext.getExecutorServiceManager().registerThreadPoolProfile(poolProfile.build());

                log.info("[CAMEL-INIT] Đã đăng ký ThreadPoolProfile 'customHubParallelPool' (10-50 threads, queue: 1000)");

                // 2. Kích hoạt Stream Caching (đọc Payload nhiều lần mà không cạn luồng)
                camelContext.setStreamCaching(true);

                // 3. Kích hoạt Log Masking (Tự động che giấu số thẻ, mật khẩu, CCCD)
                camelContext.setLogMask(true);

                // 4. Tắt tracing chi tiết ở mức context để tối ưu hiệu năng
                camelContext.setTracing(false);

                // 5. Tự động nạp toàn bộ các Workflow YAML (kiểu GitHub Actions) từ classpath:workflows/*.yaml
                try {
                    org.springframework.core.io.support.PathMatchingResourcePatternResolver resolver =
                            new org.springframework.core.io.support.PathMatchingResourcePatternResolver();
                    org.springframework.core.io.Resource[] resources = resolver.getResources("classpath*:workflows/*.yaml");
                    for (org.springframework.core.io.Resource res : resources) {
                        if (res.isReadable()) {
                            try {
                                log.info("[WORKFLOW-LOADER] Đang nạp Declarative Workflow: {}", res.getFilename());
                                org.apache.camel.spi.Resource camelRes = org.apache.camel.support.ResourceHelper.fromBytes(
                                        res.getFilename(), res.getInputStream().readAllBytes()
                                );
                                org.apache.camel.support.PluginHelper.getRoutesLoader(camelContext).loadRoutes(camelRes);
                            } catch (Exception fileEx) {
                                log.error("[WORKFLOW-LOADER] Lỗi khi nạp file Workflow {}: {}", res.getFilename(), fileEx.getMessage(), fileEx);
                                throw fileEx;
                            }
                        }
                    }
                } catch (Exception ex) {
                    log.error("[WORKFLOW-LOADER] Quá trình nạp Workflow YAML gặp lỗi: {}", ex.getMessage(), ex);
                    throw new RuntimeException("Lỗi nạp Declarative Workflow YAML", ex);
                }
            }

            @Override
            public void afterApplicationStart(CamelContext camelContext) {
                log.info("[CAMEL-READY] CamelContext '{}' đã khởi động hoàn tất với {} routes đang hoạt động!",
                        camelContext.getName(), camelContext.getRoutesSize());
            }
        };
    }
}
