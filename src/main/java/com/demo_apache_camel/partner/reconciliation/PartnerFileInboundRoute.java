package com.demo_apache_camel.partner.reconciliation;

import lombok.extern.slf4j.Slf4j;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

/**
 * [PHÂN HỆ ĐỐI SOÁT & TÁC VỤ LÔ - PARTNER FILE INBOUND ORCHESTRATOR]
 * Tuyến điều phối tiếp nhận và xử lý file dữ liệu theo lô từ các đối tác:
 * Tự động phân loại định dạng file (CSV, XML, JSON, Fixed-Length TXT) và chuyển tiếp tới Parser tương ứng.
 */
@Slf4j
@Component
public class PartnerFileInboundRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {

        // Tuyến tiếp nhận và điều phối file theo định dạng tên file
        from("direct:processInboundFile")
            .routeId("partner-file-inbound-orchestrator")
            .log("[FILE-INBOUND] Nhận file đối soát: ${header.CamelFileName}")
            .choice()
                .when(header("CamelFileName").endsWith(".csv"))
                    .log("[FILE-INBOUND] => Chuyển sang Parser Viettel CSV")
                    .to("direct:parseViettelCsv")

                .when(header("CamelFileName").endsWith(".xml"))
                    .log("[FILE-INBOUND] => Chuyển sang Parser Sabre GDS XML")
                    .to("direct:parseSabreXml")

                .when(header("CamelFileName").endsWith(".json"))
                    .log("[FILE-INBOUND] => Chuyển sang Parser VinWonders JSON")
                    .to("direct:parseVinwondersJson")

                .when(header("CamelFileName").endsWith(".txt"))
                    .log("[FILE-INBOUND] => Chuyển sang Parser Napas Fixed-Length TXT")
                    .to("direct:parseNapasFixedLength")

                .otherwise()
                    .log("[FILE-INBOUND] Định dạng file không được hỗ trợ: ${header.CamelFileName}")
            .end();
    }
}
