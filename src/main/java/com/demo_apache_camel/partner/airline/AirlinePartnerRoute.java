package com.demo_apache_camel.partner.airline;

import com.demo_apache_camel.hub.aggregator.LowestPriceAggregator;
import com.demo_apache_camel.hub.model.OrderRequest;
import com.demo_apache_camel.hub.model.OrderResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * [PHÂN HỆ HÀNG KHÔNG - AIRLINE PARTNER ORCHESTRATOR]
 * Tuyến điều phối nghiệp vụ Hàng không:
 * 1. Scatter-Gather EIP: Gửi yêu cầu song song tới Vietjet và Vietnam Airlines.
 * 2. Gather: LowestPriceAggregator so khớp và trả về vé rẻ nhất.
 * 3. Booking Gateway: Đặt và cấp mã giữ chỗ PNR chính thức.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AirlinePartnerRoute extends RouteBuilder {

    private final LowestPriceAggregator lowestPriceAggregator;

    @Override
    public void configure() throws Exception {

        // 1. SCATTER-GATHER: Tìm kiếm vé máy bay đa hãng song song
        from("direct:flightPartnerService")
            .routeId("airline-partner-orchestrator")
            .setProperty("OriginalRequest", body())
            .log("[AIRLINE-HUB] Kích hoạt Scatter-Gather tìm vé song song từ Vietjet và Vietnam Airlines...")
            .multicast(lowestPriceAggregator)
                .parallelProcessing()
                .executorService("customHubParallelPool")
                .to("direct:callVietjetAir", "direct:callVietnamAirlines")
            .end()
            .log("[AIRLINE-HUB] Đã chọn vé tối ưu nhất: ${body.airline} - ${body.price} VND (Chuyến: ${body.flightNumber})")
            .process(exchange -> {
                FlightQuote best = exchange.getMessage().getBody(FlightQuote.class);
                OrderRequest req = exchange.getProperty("OriginalRequest", OrderRequest.class);
                exchange.getMessage().setBody(new OrderResponse(
                    req != null ? req.getOrderId() : "N/A",
                    "SUCCESS",
                    "FLIGHT",
                    best != null ? best.getPrice() : 0.0,
                    String.format("Tìm thấy vé rẻ nhất: %s (%s) giá %,.0f VND", 
                            best != null ? best.getAirline() : "N/A", 
                            best != null ? best.getFlightNumber() : "N/A", 
                            best != null ? best.getPrice() : 0.0),
                    best != null ? best.getAirline() : "AIRLINE_HUB"
                ));
            });

        // 2. Tuyến Đặt & Giữ chỗ vé máy bay (Booking & PNR Issuance)
        from("direct:flightBookingPartnerService")
            .routeId("airline-booking-orchestrator")
            .process(exchange -> {
                OrderRequest req = exchange.getMessage().getBody(OrderRequest.class);
                String pnr = "PNR-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
                exchange.getMessage().setBody(new OrderResponse(
                    req != null ? req.getOrderId() : "N/A",
                    "SUCCESS",
                    "FLIGHT_BOOKING",
                    req != null && req.getAmount() != null ? req.getAmount() : 1850000.0,
                    String.format("Đặt và giữ vé thành công! Mã PNR: %s (Hành khách: %s)", 
                            pnr, req != null ? req.getCustomerName() : "Khách hàng"),
                    "AIRLINE_BOOKING_GATEWAY"
                ));
            });
    }
}
