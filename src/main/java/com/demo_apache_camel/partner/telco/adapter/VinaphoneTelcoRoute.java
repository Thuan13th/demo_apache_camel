package com.demo_apache_camel.partner.telco.adapter;

import com.demo_apache_camel.hub.model.OrderRequest;
import com.demo_apache_camel.hub.model.OrderResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * [TELCO ADAPTER - VINAPHONE]
 * Adapter xử lý nạp thẻ Vinaphone (Chiết khấu 4%), hỗ trợ mô phỏng lỗi gián đoạn mạng
 * để kích hoạt cơ chế Retry & Fallback của Hub.
 */
@Slf4j
@Component
public class VinaphoneTelcoRoute extends RouteBuilder {

    private final AtomicInteger failCounter = new AtomicInteger(0);

    @Override
    public void configure() throws Exception {
        from("direct:callVinaTelco")
            .routeId("adapter-vina-telco")
            .errorHandler(noErrorHandler())
            .process(exchange -> {
                OrderRequest req = exchange.getMessage().getBody(OrderRequest.class);
                boolean isError = "true".equalsIgnoreCase(exchange.getIn().getHeader("X-Simulate-Error", String.class))
                        || (req.getAmount() != null && req.getAmount() == 999999.0);

                if (isError) {
                    int count = failCounter.incrementAndGet();
                    log.warn("[ADAPTER-VINA] Kênh truyền Vinaphone gián đoạn (Lần thử {})! Đang kích hoạt Camel Retry...", count);
                    throw new IllegalStateException("Kênh truyền Vinaphone 503 Service Unavailable");
                }

                exchange.getMessage().setBody(new OrderResponse(
                    req.getOrderId(),
                    "SUCCESS",
                    "TOPUP",
                    req.getAmount() != null ? req.getAmount() * 0.96 : 0.0,
                    "Nạp thẻ Vinaphone thành công (Chiết khấu 4%)",
                    "VINAPHONE_TELCO_DIRECT"
                ));
            });
    }
}
