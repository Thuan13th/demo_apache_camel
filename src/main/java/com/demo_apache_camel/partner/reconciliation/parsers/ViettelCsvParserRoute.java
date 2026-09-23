package com.demo_apache_camel.partner.reconciliation.parsers;

import com.demo_apache_camel.hub.model.OrderResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * [RECONCILIATION PARSER - VIETTEL CSV]
 * Bóc tách file sao kê Topup CSV phân cách bởi dấu gạch đứng (|).
 */
@Slf4j
@Component
public class ViettelCsvParserRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        from("direct:parseViettelCsv")
            .routeId("parser-viettel-csv")
            .log("[RECON-CSV] Bắt đầu phân tích file đối soát Viettel...")
            .process(exchange -> {
                String content = exchange.getMessage().getBody(String.class);
                List<OrderResponse> results = new ArrayList<>();

                if (content != null && !content.trim().isEmpty()) {
                    String[] lines = content.split("\\r?\\n");
                    // Bỏ qua dòng Header đầu tiên (index 0)
                    for (int i = 1; i < lines.length; i++) {
                        String line = lines[i].trim();
                        if (line.isEmpty()) continue;

                        String[] fields = line.split("\\|");
                        if (fields.length >= 6) {
                            String partnerRefId = fields[1];
                            String amountStr = fields[4];
                            String status = fields[5];

                            results.add(new OrderResponse(
                                partnerRefId,
                                status,
                                "TOPUP",
                                Double.parseDouble(amountStr),
                                "Đối soát thành công từ file Viettel CSV",
                                "VIETTEL_CSV_RECON"
                            ));
                        }
                    }
                }
                log.info("[RECON-CSV] Đã bóc tách thành công {} bản ghi từ file Viettel", results.size());
                exchange.getMessage().setBody(results);
            });
    }
}
