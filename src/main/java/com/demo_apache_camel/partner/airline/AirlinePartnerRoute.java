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
 * [PHÂN HỆ HÀNG KHÔNG - AIRLINE PARTNER INTEGRATION]
 * Hiện thực hóa mẫu thiết kế kinh điển: SCATTER-GATHER EIP
 * 1. Scatter: Bắn yêu cầu khảo giá vé song song sang Vietjet Air và Vietnam Airlines.
 * 2. Gather: LowestPriceAggregator tự động so khớp và chọn chuyến bay có giá rẻ nhất cho khách.
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
                    best.getPrice(),
                    String.format("Tìm thấy vé rẻ nhất: %s (%s) giá %,.0f VND", best.getAirline(), best.getFlightNumber(), best.getPrice()),
                    best.getAirline()
                ));
            });

        // 2. Adapter Vietjet Air
        from("direct:callVietjetAir")
            .routeId("adapter-vietjet-air")
            .delay(100)
            .process(exchange -> {
                OrderRequest req = exchange.getProperty("OriginalRequest", OrderRequest.class);
                String dest = (req != null && req.getDestination() != null) ? req.getDestination() : "HAN";
                exchange.getMessage().setBody(new FlightQuote("Vietjet Air", "VJ-152", 1450000.0, "SGN", dest));
            });

        // 3. Adapter Vietnam Airlines
        from("direct:callVietnamAirlines")
            .routeId("adapter-vietnam-airlines")
            .delay(100)
            .process(exchange -> {
                OrderRequest req = exchange.getProperty("OriginalRequest", OrderRequest.class);
                String dest = (req != null && req.getDestination() != null) ? req.getDestination() : "HAN";
                exchange.getMessage().setBody(new FlightQuote("Vietnam Airlines", "VN-246", 1850000.0, "SGN", dest));
            });

        // 4. Tuyến Đặt & Giữ chỗ (Booking & Ticket Issuing)
        from("direct:flightBookingPartnerService")
            .routeId("airline-booking-orchestrator")
            .process(exchange -> {
                OrderRequest req = exchange.getMessage().getBody(OrderRequest.class);
                String pnr = "PNR-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
                exchange.getMessage().setBody(new OrderResponse(
                    req.getOrderId(),
                    "SUCCESS",
                    "FLIGHT_BOOKING",
                    req.getAmount() != null ? req.getAmount() : 1850000.0,
                    String.format("Đặt và giữ vé thành công! Mã PNR: %s (Hành khách: %s)", pnr, req.getCustomerName()),
                    "AIRLINE_BOOKING_GATEWAY"
                ));
            });
    }
}
