package com.demo_apache_camel.partner.reconciliation.parsers;

import com.demo_apache_camel.hub.model.OrderResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * [RECONCILIATION PARSER - VINWONDERS JSON]
 * Bóc tách file JSON Batch chứa danh sách vé vui chơi VinWonders.
 */
@Slf4j
@Component
public class VinwondersJsonParserRoute extends RouteBuilder {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void configure() throws Exception {
        from("direct:parseVinwondersJson")
            .routeId("parser-vinwonders-json")
            .log("[RECON-JSON] Bắt đầu phân tích file vé VinWonders JSON...")
            .process(exchange -> {
                String jsonContent = exchange.getMessage().getBody(String.class);
                List<OrderResponse> results = new ArrayList<>();

                if (jsonContent != null && !jsonContent.trim().isEmpty()) {
                    JsonNode root = objectMapper.readTree(jsonContent);
                    String parkCode = root.path("parkCode").asText("VINWONDERS");
                    JsonNode ticketsNode = root.path("data");

                    if (ticketsNode.isArray()) {
                        for (JsonNode ticket : ticketsNode) {
                            String bookingId = ticket.path("bookingId").asText();
                            String ticketType = ticket.path("ticketType").asText();
                            String qrCode = ticket.path("qrCode").asText();
                            double price = ticket.path("price").asDouble(0.0);

                            results.add(new OrderResponse(
                                bookingId,
                                "SUCCESS",
                                "ATTRACTION",
                                price,
                                String.format("Vé %s: %s (QR: %s)", parkCode, ticketType, qrCode),
                                "VINWONDERS_JSON_RECON"
                            ));
                        }
                    }
                }
                log.info("[RECON-JSON] Đã bóc tách thành công {} vé từ JSON VinWonders", results.size());
                exchange.getMessage().setBody(results);
            });
    }
}
