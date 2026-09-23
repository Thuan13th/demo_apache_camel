package com.demo_apache_camel.partner.reconciliation.parsers;

import com.demo_apache_camel.hub.model.OrderResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * [RECONCILIATION PARSER - SABRE XML]
 * Bóc tách file XML xuất vé máy bay Sabre GDS chuẩn IATA.
 */
@Slf4j
@Component
public class SabreXmlParserRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        from("direct:parseSabreXml")
            .routeId("parser-sabre-xml")
            .log("[RECON-XML] Bắt đầu phân tích file đặt vé Sabre XML...")
            .process(exchange -> {
                String xmlContent = exchange.getMessage().getBody(String.class);
                List<OrderResponse> results = new ArrayList<>();

                if (xmlContent != null && !xmlContent.trim().isEmpty()) {
                    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                    DocumentBuilder builder = factory.newDocumentBuilder();
                    Document doc = builder.parse(new InputSource(new StringReader(xmlContent)));

                    NodeList orderNodes = doc.getElementsByTagName("Order");
                    for (int i = 0; i < orderNodes.getLength(); i++) {
                        Element orderElem = (Element) orderNodes.item(i);
                        String orderRef = orderElem.getAttribute("orderRef");
                        String pnrCode = orderElem.getAttribute("pnrCode");

                        String airlineCode = getTagValue("AirlineCode", orderElem);
                        String flightNumber = getTagValue("FlightNumber", orderElem);
                        String priceStr = getTagValue("Price", orderElem);
                        String status = getTagValue("Status", orderElem);

                        double price = (priceStr != null && !priceStr.isEmpty()) ? Double.parseDouble(priceStr) : 0.0;

                        results.add(new OrderResponse(
                            orderRef,
                            "CONFIRMED".equalsIgnoreCase(status) ? "SUCCESS" : status,
                            "FLIGHT_BOOKING",
                            price,
                            String.format("Vé Sabre GDS: %s (%s) - PNR: %s", airlineCode, flightNumber, pnrCode),
                            "SABRE_XML_RECON"
                        ));
                    }
                }
                log.info("[RECON-XML] Đã bóc tách thành công {} vé máy bay từ XML Sabre", results.size());
                exchange.getMessage().setBody(results);
            });
    }

    private String getTagValue(String tag, Element element) {
        NodeList nodeList = element.getElementsByTagName(tag);
        if (nodeList.getLength() > 0 && nodeList.item(0) != null) {
            return nodeList.item(0).getTextContent().trim();
        }
        return "";
    }
}
