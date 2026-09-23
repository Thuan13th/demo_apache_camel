package com.demo_apache_camel.hub.routes;

import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

/**
 * [HUB DEAD LETTER QUEUE PROCESSOR]
 * Tuyến tiếp nhận và xử lý các bản tin lỗi nghiêm trọng (vượt quá số lần Retry):
 * Lưu vết lỗi chi tiết, trích xuất điểm thất bại và chuyển tiếp sang bộ phận vận hành tra soát.
 */
@Slf4j
@Component
public class DeadLetterRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {

        from("seda:deadLetterQueue")
            .routeId("hub-dead-letter-route")
            .process(exchange -> {
                Exception cause = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
                String failedEndpoint = exchange.getProperty(Exchange.FAILURE_ENDPOINT, String.class);
                String originalBody = exchange.getMessage().getBody(String.class);

                log.error("[DEAD-LETTER-ALERT] Giao dịch thất bại hoàn toàn! Endpoint: {}, Lỗi: {}, Payload gốc: {}",
                        failedEndpoint != null ? failedEndpoint : "N/A",
                        cause != null ? cause.getMessage() : "Unknown Exception",
                        originalBody);
            })
            .log("[DEAD-LETTER] Đã ghi nhận bản tin lỗi vào kho dữ liệu phục hồi sự cố");
    }
}
