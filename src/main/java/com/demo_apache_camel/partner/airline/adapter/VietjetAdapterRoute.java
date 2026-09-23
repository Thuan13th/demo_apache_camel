package com.demo_apache_camel.partner.airline.adapter;

import com.demo_apache_camel.hub.model.OrderRequest;
import com.demo_apache_camel.partner.airline.FlightQuote;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

/**
 * [AIRLINE ADAPTER - VIETJET AIR]
 * Adapter kết nối Vietjet Air: Khảo giá và tính cước chặng bay.
 */
@Component
public class VietjetAdapterRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        from("direct:callVietjetAir")
            .routeId("adapter-vietjet-air")
            .delay(100)
            .process(exchange -> {
                OrderRequest req = exchange.getProperty("OriginalRequest", OrderRequest.class);
                String dest = (req != null && req.getDestination() != null) ? req.getDestination() : "HAN";
                exchange.getMessage().setBody(new FlightQuote("Vietjet Air", "VJ-152", 1450000.0, "SGN", dest));
            });
    }
}
