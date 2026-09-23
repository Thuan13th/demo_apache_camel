package com.demo_apache_camel.hub.aggregator;

import com.demo_apache_camel.partner.airline.FlightQuote;
import org.apache.camel.AggregationStrategy;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * [HUB AGGREGATOR]
 * Chiến lược gộp và chọn lọc của Hub (Scatter-Gather Pattern):
 * So sánh báo giá từ các hãng hàng không đối tác và chọn vé có giá tốt nhất cho khách hàng.
 */
@Component
public class LowestPriceAggregator implements AggregationStrategy {

    private static final Logger log = LoggerFactory.getLogger(LowestPriceAggregator.class);

    @Override
    public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
        if (oldExchange == null) {
            FlightQuote firstQuote = newExchange.getMessage().getBody(FlightQuote.class);
            log.info("[HUB-AGGREGATOR] Nhận báo giá đầu tiên từ {}: {} VND", 
                    firstQuote != null ? firstQuote.getAirline() : "N/A",
                    firstQuote != null ? firstQuote.getPrice() : 0);
            return newExchange;
        }

        FlightQuote currentBest = oldExchange.getMessage().getBody(FlightQuote.class);
        FlightQuote incomingQuote = newExchange.getMessage().getBody(FlightQuote.class);

        log.info("[HUB-AGGREGATOR] So sánh đối tác: Hiện tại ({}: {} VND) vs Mới đến ({}: {} VND)",
                currentBest.getAirline(), currentBest.getPrice(),
                incomingQuote.getAirline(), incomingQuote.getPrice());

        if (incomingQuote != null && incomingQuote.getPrice() < currentBest.getPrice()) {
            log.info("[HUB-AGGREGATOR] => Chọn vé ưu đãi hơn của {}", incomingQuote.getAirline());
            return newExchange;
        }

        log.info("[HUB-AGGREGATOR] => Giữ nguyên vé tốt hơn của {}", currentBest.getAirline());
        return oldExchange;
    }
}
