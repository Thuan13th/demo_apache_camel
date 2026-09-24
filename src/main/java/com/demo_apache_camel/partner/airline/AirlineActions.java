package com.demo_apache_camel.partner.airline;

import com.demo_apache_camel.hub.model.OrderRequest;
import com.demo_apache_camel.hub.model.OrderResponse;
import org.apache.camel.Exchange;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * [AIRLINE ACTIONS BEAN]
 * Các action xử lý nghiệp vụ hàng không được Workflow YAML (03-airline-flight-workflow.yaml) gọi tới.
 * Thiết kế theo chuẩn GitHub Custom Actions: tách rời orchestration (YAML) khỏi domain logic (Java Bean).
 */
@Component("airlineActions")
public class AirlineActions {

    public FlightQuote quoteVietjet(Exchange exchange) {
        OrderRequest req = exchange.getProperty("OriginalRequest", OrderRequest.class);
        String dest = (req != null && req.getDestination() != null) ? req.getDestination() : "HAN";
        return new FlightQuote("Vietjet Air", "VJ-152", 1450000.0, "SGN", dest);
    }

    public FlightQuote quoteVna(Exchange exchange) {
        OrderRequest req = exchange.getProperty("OriginalRequest", OrderRequest.class);
        String dest = (req != null && req.getDestination() != null) ? req.getDestination() : "HAN";
        return new FlightQuote("Vietnam Airlines", "VN-246", 1850000.0, "SGN", dest);
    }

    public OrderResponse finalizeBestQuote(Exchange exchange) {
        FlightQuote best = exchange.getMessage().getBody(FlightQuote.class);
        OrderRequest req = exchange.getProperty("OriginalRequest", OrderRequest.class);
        return new OrderResponse(
            req != null ? req.getOrderId() : "N/A",
            "SUCCESS",
            "FLIGHT",
            best != null ? best.getPrice() : 0.0,
            String.format("Tìm thấy vé rẻ nhất: %s (%s) giá %,.0f VND",
                    best != null ? best.getAirline() : "N/A",
                    best != null ? best.getFlightNumber() : "N/A",
                    best != null ? best.getPrice() : 0.0),
            best != null ? best.getAirline() : "AIRLINE_HUB"
        );
    }

    public OrderResponse bookFlight(OrderRequest req) {
        String pnr = "PNR-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        return new OrderResponse(
            req != null ? req.getOrderId() : "N/A",
            "SUCCESS",
            "FLIGHT_BOOKING",
            req != null && req.getAmount() != null ? req.getAmount() : 1850000.0,
            String.format("Đặt và giữ vé thành công! Mã PNR: %s (Hành khách: %s)",
                    pnr, req != null ? req.getCustomerName() : "Khách hàng"),
            "AIRLINE_BOOKING_GATEWAY"
        );
    }
}
