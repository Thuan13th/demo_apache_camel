package com.demo_apache_camel.partner.airline.adapter;

import com.demo_apache_camel.hub.model.OrderRequest;
import com.demo_apache_camel.partner.airline.FlightQuote;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

/**
 * [AIRLINE ADAPTER - VIETNAM AIRLINES]
 * Adapter kết nối Vietnam Airlines (Sabre GDS): Khảo giá chặng bay quốc gia.
 */
@Component
public class VietnamAirlinesAdapterRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        from("direct:callVietnamAirlines")
            .routeId("adapter-vietnam-airlines")
            .delay(100)
            .process(exchange -> {
                OrderRequest req = exchange.getProperty("OriginalRequest", OrderRequest.class);
                String dest = (req != null && req.getDestination() != null) ? req.getDestination() : "HAN";
                exchange.getMessage().setBody(new FlightQuote("Vietnam Airlines", "VN-246", 1850000.0, "SGN", dest));
            });
    }
}
