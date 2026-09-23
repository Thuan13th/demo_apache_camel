package com.demo_apache_camel.partner.reconciliation.parsers;

import com.demo_apache_camel.hub.model.OrderResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * [RECONCILIATION PARSER - NAPAS FIXED-LENGTH TXT]
 * Bóc tách file sao kê giao dịch tài chính chuẩn Napas độ dài cố định (Fixed-Length).
 */
@Slf4j
@Component
public class NapasFixedLengthParserRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        from("direct:parseNapasFixedLength")
            .routeId("parser-napas-fixed-length")
            .log("[RECON-NAPAS] Bắt đầu phân tích file sao kê Napas Fixed-Length...")
            .process(exchange -> {
                String content = exchange.getMessage().getBody(String.class);
                List<OrderResponse> results = new ArrayList<>();

                if (content != null && !content.trim().isEmpty()) {
                    String[] lines = content.split("\\r?\\n");
                    for (String line : lines) {
                        line = line.trim();
                        // Chỉ bóc tách các dòng Detail (bắt đầu bằng 'D')
                        if (line.startsWith("D") && line.length() >= 49) {
                            String orderId = line.substring(8, 20).trim();
                            String amountRaw = line.substring(20, 32).trim();
                            String status = line.substring(42, 49).trim();

                            double amount = 0.0;
                            try {
                                amount = Double.parseDouble(amountRaw);
                            } catch (NumberFormatException ignored) {}

                            results.add(new OrderResponse(
                                orderId,
                                status,
                                "PAYMENT",
                                amount,
                                "Đối soát khớp lệnh thành công từ Napas Fixed-Length",
                                "NAPAS_TXT_RECON"
                            ));
                        }
                    }
                }
                log.info("[RECON-NAPAS] Đã bóc tách thành công {} giao dịch từ file Napas", results.size());
                exchange.getMessage().setBody(results);
            });
    }
}
